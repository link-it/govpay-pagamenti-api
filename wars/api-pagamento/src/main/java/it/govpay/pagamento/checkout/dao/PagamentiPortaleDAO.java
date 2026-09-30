/*
 * GovPay - Porta di Accesso al Nodo dei Pagamenti SPC
 * http://www.gov4j.it/govpay
 *
 * Copyright (c) 2014-2026 Link.it srl (http://www.link.it).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3, as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package it.govpay.pagamento.checkout.dao;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang3.StringUtils;
import org.openspcoop2.generic_project.exception.NotFoundException;
import org.openspcoop2.generic_project.exception.ServiceException;
import org.openspcoop2.utils.LoggerWrapperFactory;
import org.openspcoop2.utils.UtilsException;
import org.openspcoop2.utils.logger.beans.Property;
import org.openspcoop2.utils.service.context.ContextThreadLocal;
import org.openspcoop2.utils.service.context.IContext;
import org.slf4j.Logger;

import it.gov.pagopa.checkout.model.CartRequest;
import it.govpay.bd.BDConfigWrapper;
import it.govpay.bd.anagrafica.AnagraficaManager;
import it.govpay.bd.model.Configurazione;
import it.govpay.bd.model.Dominio;
import it.govpay.bd.model.Stazione;
import it.govpay.bd.model.TipoVersamentoDominio;
import it.govpay.bd.model.UnitaOperativa;
import it.govpay.bd.model.Versamento;
import it.govpay.core.autorizzazione.AuthorizationManager;
import it.govpay.core.autorizzazione.beans.GovpayLdapUserDetails;
import it.govpay.core.autorizzazione.utils.AutorizzazioneUtils;
import it.govpay.core.beans.EsitoOperazione;
import it.govpay.core.beans.EventoContext;
import it.govpay.core.beans.EventoContext.Componente;
import it.govpay.core.beans.GpResponse;
import it.govpay.core.beans.tracciati.PendenzaPost;
import it.govpay.core.dao.commons.BaseDAO;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTO;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTO.RefVersamentoAvviso;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTO.RefVersamentoModello4;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTO.RefVersamentoPendenza;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTOResponse;
import it.govpay.core.exceptions.EcException;
import it.govpay.core.exceptions.GovPayException;
import it.govpay.core.exceptions.IOException;
import it.govpay.core.exceptions.NotAuthorizedException;
import it.govpay.core.exceptions.UnprocessableEntityException;
import it.govpay.core.exceptions.ValidationException;
import it.govpay.pagamento.checkout.utils.CheckoutUtils;
import it.govpay.core.utils.GovpayConfig;
import it.govpay.core.utils.GpContext;
import it.govpay.core.utils.LogUtils;
import it.govpay.core.utils.TracciatiConverter;
import it.govpay.core.utils.VersamentoUtils;
import it.govpay.pagamento.checkout.client.CheckoutClient;
import it.govpay.core.utils.client.exception.ClientException;
import it.govpay.core.utils.client.exception.ClientInitializeException;
import it.govpay.core.utils.logger.MessaggioDiagnosticoCostanti;
import it.govpay.core.utils.logger.MessaggioDiagnosticoUtils;
import it.govpay.core.utils.tracciati.validator.PendenzaPostValidator;
import it.govpay.model.Anagrafica;
import it.govpay.model.Stazione.Versione;
import it.govpay.model.TipoVersamento;
import it.govpay.model.Utenza.TIPO_UTENZA;
import it.govpay.model.Versamento.TipologiaTipoVersamento;
import it.govpay.model.configurazione.Giornale;
import it.govpay.model.exception.CodificaInesistenteException;

/**
 * Avvio di un pagamento tramite Checkout PagoPA (POST /pagamenti API Pagamento v2).
 *
 * Backport dalla versione 3.9.1 limitato al solo caso d'uso Checkout PagoPA (stazione V2).
 * Il Checkout e' sempre utilizzato: la property it.govpay.checkout.enabled non viene considerata.
 */
public class PagamentiPortaleDAO extends BaseDAO {

	public PagamentiPortaleDAO() {
		super();
	}

	public PagamentiPortaleDTOResponse inserisciPagamenti(PagamentiPortaleDTO pagamentiPortaleDTO) 
			throws IOException, CodificaInesistenteException, GovPayException, NotAuthorizedException, ServiceException, UtilsException, ValidationException, EcException, UnprocessableEntityException { 
		BDConfigWrapper configWrapper = new BDConfigWrapper(ContextThreadLocal.get().getTransactionId(), this.useCacheData);
		PagamentiPortaleDTOResponse response  = new PagamentiPortaleDTOResponse();
		GpResponse transazioneResponse = new GpResponse();
		Logger log = LoggerWrapperFactory.getLogger(PagamentiPortaleDAO.class);

		IContext ctx = ContextThreadLocal.get();

		if(ctx == null)
			throw new GovPayException(EsitoOperazione.INTERNAL, "Inizializzazione contesto fallita.");

		GpContext appContext = (GpContext) ctx.getApplicationContext();

		if(ctx == null || appContext==null || appContext.getPagamentoCtx() == null || appContext.getRequest()==null)
			throw new GovPayException(EsitoOperazione.INTERNAL, "Inizializzazione contesto fallita.");

		((GpContext) (ContextThreadLocal.get()).getApplicationContext()).getEventoCtx().setIdPagamento(pagamentiPortaleDTO.getIdSessione());
		try {
			GovpayLdapUserDetails userDetails = AutorizzazioneUtils.getAuthenticationDetails(pagamentiPortaleDTO.getUser());
			List<Versamento> versamenti = new ArrayList<>();

			// Aggiungo il codSessionePortale al PaymentContext
			appContext.getPagamentoCtx().setCodSessionePortale(pagamentiPortaleDTO.getIdSessionePortale());
			appContext.getRequest().addGenericProperty(new Property("codSessionePortale", pagamentiPortaleDTO.getIdSessionePortale() != null ? pagamentiPortaleDTO.getIdSessionePortale() : "--Non fornito--"));

			MessaggioDiagnosticoUtils.logMessaggioDiagnostico(log, ctx, MessaggioDiagnosticoCostanti.MSG_DIAGNOSTICO_WS_RICEVUTA_RICHIESTA);
			MessaggioDiagnosticoUtils.logMessaggioDiagnostico(log, ctx, MessaggioDiagnosticoCostanti.MSG_DIAGNOSTICO_WS_AUTORIZZAZIONE);

			it.govpay.core.business.Versamento versamentoBusiness = new it.govpay.core.business.Versamento();
			Anagrafica versanteModel = VersamentoUtils.toAnagraficaModel(pagamentiPortaleDTO.getVersante());
			// 1. Lista Id_versamento
			for(int i = 0; i < pagamentiPortaleDTO.getPendenzeOrPendenzeRef().size(); i++) {
				Object v = pagamentiPortaleDTO.getPendenzeOrPendenzeRef().get(i);
				Versamento versamentoModel = null;
				if(v instanceof it.govpay.core.beans.commons.Versamento versamento) {
					MessaggioDiagnosticoUtils.logMessaggioDiagnostico(log, ctx, MessaggioDiagnosticoCostanti.MSG_DIAGNOSTICO_RPT_ACQUISIZIONE_VERSAMENTO, versamento.getCodApplicazione(), versamento.getCodVersamentoEnte());
					versamentoModel = versamentoBusiness.chiediVersamento(versamento);
					versamentoModel.setTipo(TipologiaTipoVersamento.SPONTANEO);

					// se l'utenza che ha caricato la pendenza inline e' un cittadino sono necessari dei controlli supplementari.
					if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.CITTADINO)) {

						// se il tributo non puo' essere pagato da terzi allora debitore e versante (se presente) devono coincidere con chi sta effettuando il pagamento.
						if(!versamentoModel.getTipoVersamentoDominio(configWrapper).isPagaTerzi()) {
							if(!versamento.getDebitore().getCodUnivoco().equals(userDetails.getIdentificativo()))
								throw new GovPayException(EsitoOperazione.CIT_003, userDetails.getIdentificativo(),versamentoModel.getApplicazione(configWrapper).getCodApplicazione(), versamentoModel.getCodVersamentoEnte(),versamento.getDebitore().getCodUnivoco());

							if(versanteModel != null && !versanteModel.getCodUnivoco().equals(userDetails.getIdentificativo()))
								throw new GovPayException(EsitoOperazione.CIT_004, userDetails.getIdentificativo(),versamentoModel.getApplicazione(configWrapper).getCodApplicazione(), versamentoModel.getCodVersamentoEnte(),versanteModel.getCodUnivoco());
						}

					}
				}  else if(v instanceof RefVersamentoAvviso refVersamentoAvviso) {
					String idDominio = refVersamentoAvviso.getIdDominio();
					String cfToCheck = refVersamentoAvviso.getIdDebitore();
					try {
						Dominio dominio = AnagraficaManager.getDominio(configWrapper, idDominio);

						if(!dominio.isAbilitato())
							throw new GovPayException(EsitoOperazione.DOM_001, dominio.getCodDominio());

						versamentoModel = versamentoBusiness.chiediVersamentoRifAvviso(refVersamentoAvviso.getIdDominio(), refVersamentoAvviso.getNumeroAvviso());

						// controllo che l'utenza anonima possa effettuare il pagamento dell'avviso	
						if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.ANONIMO)) {
							this.checkCFDebitoreVersamento(pagamentiPortaleDTO.getUser(), cfToCheck, versamentoModel.getAnagraficaDebitore().getCodUnivoco());
						}

					}catch(NotFoundException e) {
						throw new GovPayException("Il pagamento non puo' essere avviato poiche' uno dei versamenti risulta associato ad un dominio non disponibile [Dominio:"+idDominio+"].", EsitoOperazione.DOM_000, idDominio);
					}
				}  else if(v instanceof RefVersamentoPendenza refVersamentoPendenza) {
					// controllo se le pendenze richieste siano a disposizione in sessione altrimenti assumo che siano dei dovuti gia' caricati
					if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.CITTADINO) || userDetails.getTipoUtenza().equals(TIPO_UTENZA.ANONIMO)) {
						String idA2A = refVersamentoPendenza.getIdA2A();
						String idPendenza = refVersamentoPendenza.getIdPendenza();

						if(pagamentiPortaleDTO.getListaPendenzeDaSessione() != null && pagamentiPortaleDTO.getListaPendenzeDaSessione().containsKey((idA2A+idPendenza))) {
							MessaggioDiagnosticoUtils.logMessaggioDiagnostico(log, ctx, MessaggioDiagnosticoCostanti.MSG_DIAGNOSTICO_RPT_ACQUISIZIONE_VERSAMENTO, idA2A, idPendenza);
							versamentoModel = pagamentiPortaleDTO.getListaPendenzeDaSessione().get((idA2A+idPendenza));
							versamentoModel.setTipo(TipologiaTipoVersamento.SPONTANEO);

							// se l'utenza che ha caricato la pendenza inline e' un cittadino sono necessari dei controlli supplementari.
							if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.CITTADINO)) {
								// se il tributo non puo' essere pagato da terzi allora debitore e versante (se presente) devono coincidere con chi sta effettuando il pagamento.
								if(!versamentoModel.getTipoVersamentoDominio(configWrapper).isPagaTerzi()) {
									if(!versamentoModel.getAnagraficaDebitore().getCodUnivoco().equals(userDetails.getIdentificativo()))
										throw new GovPayException(EsitoOperazione.CIT_003, userDetails.getIdentificativo(),versamentoModel.getApplicazione(configWrapper).getCodApplicazione(), versamentoModel.getCodVersamentoEnte(),versamentoModel.getAnagraficaDebitore().getCodUnivoco());

									if(versanteModel != null && !versanteModel.getCodUnivoco().equals(userDetails.getIdentificativo()))
										throw new GovPayException(EsitoOperazione.CIT_004, userDetails.getIdentificativo(),versamentoModel.getApplicazione(configWrapper).getCodApplicazione(), versamentoModel.getCodVersamentoEnte(),versanteModel.getCodUnivoco());
								}

							}
							log.debug("RefVersamentoPendenza [idA2A:{}, idPendenza: {}] letto dalla lista identificativi in sessione", idA2A, idPendenza);
						} else {
							versamentoModel = versamentoBusiness.chiediVersamento(refVersamentoPendenza.getIdA2A(), refVersamentoPendenza.getIdPendenza());
						}
					} else {
						// applicazioni
						versamentoModel = versamentoBusiness.chiediVersamento(refVersamentoPendenza.getIdA2A(), refVersamentoPendenza.getIdPendenza());
					}
				} else if(v instanceof RefVersamentoModello4 refVersamentoModello4) {
					String idDominio = refVersamentoModello4.getIdDominio();
					String idTipoVersamento = refVersamentoModello4.getIdTipoPendenza();
					String dati = refVersamentoModello4.getDati();

					Dominio dominio = null;
					try {
						dominio = AnagraficaManager.getDominio(configWrapper, idDominio);
					} catch (NotFoundException e1) {
						throw new GovPayException("Il pagamento non puo' essere avviato poiche' uno dei versamenti risulta associato ad un dominio non disponibile [Dominio:"+idDominio+"].", EsitoOperazione.DOM_000, idDominio);
					}

					if(!dominio.isAbilitato())
						throw new GovPayException(EsitoOperazione.DOM_001, dominio.getCodDominio());
					// lettura della configurazione TipoVersamentoDominio
					TipoVersamentoDominio tipoVersamentoDominio = null;
					try {
						tipoVersamentoDominio = AnagraficaManager.getTipoVersamentoDominio(configWrapper, dominio.getId(), idTipoVersamento);
					} catch (NotFoundException e1) {
						throw new GovPayException("Il pagamento non puo' essere avviato poiche' uno dei versamenti risulta associato ad un tipo pendenza ["+idTipoVersamento+"] non disponibilte per il dominio ["+idDominio+"].", EsitoOperazione.TVD_000, idDominio, idTipoVersamento);
					}

					VersamentoUtils.validazioneInputVersamentoModello4(this.log, dati, tipoVersamentoDominio.getCaricamentoPendenzePortalePagamentoValidazioneDefinizione());

					MultivaluedMap<String, String> queryParameters = pagamentiPortaleDTO.getQueryParameters(); 
					MultivaluedMap<String, String> pathParameters = pagamentiPortaleDTO.getPathParameters();
					Map<String, String> headers = pagamentiPortaleDTO.getHeaders();

					String idUO = null;
					UnitaOperativa uo = null;
					boolean trasformazione = false;
					String trasformazioneDefinizione = tipoVersamentoDominio.getCaricamentoPendenzePortalePagamentoTrasformazioneDefinizione();
					String trasformazioneTipo = tipoVersamentoDominio.getCaricamentoPendenzePortalePagamentoTrasformazioneTipo();
					if(trasformazioneDefinizione != null && trasformazioneTipo != null) {
						dati = VersamentoUtils.trasformazioneInputVersamentoModello4(log, dominio, idTipoVersamento, trasformazioneTipo, uo, dati, queryParameters, pathParameters, headers, trasformazioneDefinizione);  
						trasformazione = true;
					}

					String codApplicazione = tipoVersamentoDominio.getCaricamentoPendenzePortalePagamentoCodApplicazione();
					if(codApplicazione != null) {
						versamentoModel = VersamentoUtils.inoltroInputVersamentoModello4(log, idDominio, idTipoVersamento, idUO, codApplicazione, dati);
					} else {
						try {
							PendenzaPost pendenzaPost = PendenzaPost.parse(dati);

							// imposto i dati idDominio, idTipoVersamento e idUnitaOperativa fornite nella URL di richiesta, sovrascrivendo eventuali valori impostati dalla trasformazione.
							pendenzaPost.setIdDominio(idDominio);
							pendenzaPost.setIdTipoPendenza(idTipoVersamento);
							pendenzaPost.setIdUnitaOperativa(idUO);

							new PendenzaPostValidator(pendenzaPost).validate();
							it.govpay.core.beans.commons.Versamento versamentoCommons = TracciatiConverter.getVersamentoFromPendenza(pendenzaPost);
							((GpContext) (ContextThreadLocal.get()).getApplicationContext()).getEventoCtx().setIdPendenza(versamentoCommons.getCodVersamentoEnte());
							((GpContext) (ContextThreadLocal.get()).getApplicationContext()).getEventoCtx().setIdA2A(versamentoCommons.getCodApplicazione());
							versamentoModel = versamentoBusiness.chiediVersamento(versamentoCommons);
						}catch(ValidationException | IOException e) {
							if(trasformazione) { // se la pendenza generata dalla trasformazione non e' valida restituisco errore interno
								throw new GovPayException(EsitoOperazione.VAL_003, e.getMessage());
							} else {
								throw e;
							}
						}
					}
					versamentoModel.setTipo(TipologiaTipoVersamento.SPONTANEO);
				}

				if(versamentoModel != null) {

					Dominio dominio = versamentoModel.getDominio(configWrapper); 
					UnitaOperativa uo = versamentoModel.getUo(configWrapper); 
					TipoVersamento tipoVersamento = versamentoModel.getTipoVersamento(configWrapper); 
					log.debug("Verifica autorizzazione utenza [{}], tipo [{}] al pagamento del versamento [Id: {}, IdA2A: {}] per il dominio [{}], UO [{}], tipoPendenza [{}]...",
					          userDetails.getIdentificativo(),
					          userDetails.getTipoUtenza(),
					          versamentoModel.getCodVersamentoEnte(),
					          versamentoModel.getApplicazione(configWrapper).getCodApplicazione(),
					          dominio.getCodDominio(),
					          uo.getCodUo(),
					          tipoVersamento.getCodTipoVersamento());

					if(!AuthorizationManager.isTipoVersamentoUOAuthorized(userDetails.getUtenza(), dominio.getCodDominio(), uo.getCodUo(), tipoVersamento.getCodTipoVersamento())) {
						log.warn("Non autorizzato utenza [{}], tipo [{}] al pagamento del versamento [Id: {}, IdA2A: {}] per il dominio [{}], UO [{}], tipoPendenza [{}]",
						          userDetails.getIdentificativo(),
						          userDetails.getTipoUtenza(),
						          versamentoModel.getCodVersamentoEnte(),
						          versamentoModel.getApplicazione(configWrapper).getCodApplicazione(),
						          dominio.getCodDominio(),
						          uo.getCodUo(),
						          tipoVersamento.getCodTipoVersamento());
						throw new GovPayException(EsitoOperazione.APP_003, userDetails.getIdentificativo(), versamentoModel.getApplicazione(configWrapper).getCodApplicazione(), versamentoModel.getCodVersamentoEnte());
					}

					log.debug("Autorizzato utenza [{}], tipo [{}] al pagamento del versamento [Id: {}, IdA2A: {}] per il dominio [{}], UO [{}], tipoPendenza [{}]",
					          userDetails.getIdentificativo(),
					          userDetails.getTipoUtenza(),
					          versamentoModel.getCodVersamentoEnte(),
					          versamentoModel.getApplicazione(configWrapper).getCodApplicazione(),
					          dominio.getCodDominio(),
					          uo.getCodUo(),
					          tipoVersamento.getCodTipoVersamento());


					if(!uo.isAbilitato()) {
						throw new GovPayException("Il pagamento non puo' essere avviato poiche' uno dei versamenti risulta associato ad una unita' operativa disabilitata [Uo:"+uo.getCodUo()+"].", EsitoOperazione.UOP_001, uo.getCodUo());
					}

					if(!dominio.isAbilitato()) {
						throw new GovPayException("Il pagamento non puo' essere avviato poiche' uno dei versamenti risulta associato ad un dominio disabilitato [Dominio:"+dominio.getCodDominio()+"].", EsitoOperazione.DOM_001, dominio.getCodDominio());
					}


					versamenti.add(versamentoModel);
				}
			}

			// Le pendenze del pagamento devono afferire alla stessa stazione
			Stazione stazione = null;
			Dominio dominio = null;
			for (Versamento vTmp : versamenti) {
				Dominio dominioTmp = vTmp.getDominio(configWrapper);
				if(dominio == null)	{
					dominio = dominioTmp;
				}

				if(stazione == null) {
					stazione = dominioTmp.getStazione();
				} else {
					if(stazione.getId().compareTo(dominioTmp.getStazione().getId()) != 0) {
						throw new GovPayException(EsitoOperazione.PAG_000);
					}
				}
			}

			// il checkout si puo' utilizzare solo con una stazione V2
			if(stazione == null || !Versione.V2.equals(stazione.getVersione())) {
				throw new GovPayException(EsitoOperazione.INTERNAL, "Il pagamento non puo' essere avviato: il Checkout PagoPA richiede una stazione in versione V2.");
			}

			Versione versioneStazione = stazione.getVersione();
			String codStazione = stazione.getCodStazione();
			log.debug("La stazione utilizzata [{}] ha versione [{}], invocazione verso il Checkout PagoPA...", codStazione, versioneStazione);
			String email = null;

			// urlritorno obbligatoria
			if(StringUtils.isEmpty(pagamentiPortaleDTO.getUrlRitorno())) {
				throw new ValidationException("Il campo urlRitorno non deve essere vuoto.");
			}

			// nella modalita' V1 le pendenze venivano aggiornate prima dell'invio RPT, qui forzo la generazione del numero avviso per quelle che non sono ancora inserite
			log.debug("Controllo esistenza del numero avviso per tutte le pendenze in corso...");
			for(Versamento versamento : versamenti) {
				// Aggiorno tutti i versamenti che mi sono stati passati
				String codVersamentoEnte = versamento.getCodVersamentoEnte();
				String codApplicazione = versamento.getApplicazione(configWrapper).getCodApplicazione();
				boolean create = versamento.getId() == null;
				String msg = create ? "caricamento" : "aggiornamento";
				if(!create) {
					// lettura dei singoli versamenti
					versamento.getSingoliVersamenti(configWrapper);
				}
				log.debug("Pendenza [idA2A:{}, idPendenza: {}] {} in corso...", codApplicazione, codVersamentoEnte, msg);
				versamentoBusiness.caricaVersamento(versamento, true, true, false, null, null);
				log.debug("Pendenza [idA2A:{}, idPendenza: {}] {} e generazione del numero avviso (se previsto) completati: NAV: {}", codApplicazione, codVersamentoEnte, msg, versamento.getNumeroAvviso());
			}
			log.debug("Controllo esistenza del numero avviso per tutte le pendenze completato.");

			response.setId("0");
			response.setIdSessione("0");
			// indico se restituire un redirect o un json
			response.setRedirect(GovpayConfig.getInstance().isCheckoutResponseSendRedirectEnabled());

			String checkoutBaseUrl = GovpayConfig.getInstance().getCheckoutBaseURL();
			log.debug("Url Base Checkout: {}", checkoutBaseUrl);
			String operationId = appContext.setupAppIOClient(CheckoutClient.SWAGGER_OPERATION_POST_CARTS_OPERATION_ID, checkoutBaseUrl);

			// Esecuizione della chiamata verso PagoPA
			try {
				CartRequest cartRequest = CheckoutUtils.createCartRequest(log, configWrapper, pagamentiPortaleDTO.getUrlRitorno(), versamenti, email);

				Configurazione configurazione = new it.govpay.core.business.Configurazione().getConfigurazione();
				Giornale giornale = configurazione.getGiornale();
				CheckoutClient checkoutClient = new CheckoutClient(CheckoutClient.SWAGGER_OPERATION_POST_CARTS_OPERATION_ID, checkoutBaseUrl, operationId, giornale, new EventoContext(Componente.API_PAGOPA));
				String location = checkoutClient.inviaCartRequest(cartRequest);

				log.debug("Stazione [{}] versione [{}], invocazione verso il Checkout PagoPA completata, ricevuta URL redirect [{}]", codStazione, versioneStazione, location);
				response.setLocation(location);
				response.setRedirectUrl(location);
			} catch (UnsupportedEncodingException e) {
				LogUtils.logError(log, "Errore nella decodifica della causale Versamento: " + e.getMessage(), e);
				throw new GovPayException(e);
			} catch (ClientException e) {
				LogUtils.logError(log, "Errore durante la spedizione della richiesta verso il Checkoout PagoPA: " + e.getMessage(), e);
				throw new GovPayException(e);
			} catch (ClientInitializeException e) {
				LogUtils.logError(log, "Errore durante la creazione del client per la spedizione della richiesta verso il Checkoout PagoPA: " + e.getMessage(), e);
				throw new GovPayException(e);
			}

			transazioneResponse.setCodEsito(EsitoOperazione.OK.toString());
			return response;
		}finally {
			if(ctx != null) {
				GpContext.setResult(ctx.getApplicationContext().getTransaction(), transazioneResponse);
			}
		}
	}
}
