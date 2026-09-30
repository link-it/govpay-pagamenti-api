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
package it.govpay.pagamento.v2.beans.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openspcoop2.generic_project.exception.ServiceException;
import org.slf4j.Logger;
import org.springframework.security.core.Authentication;

import it.govpay.bd.model.UtenzaCittadino;
import it.govpay.bd.model.Versamento;
import it.govpay.core.autorizzazione.beans.GovpayLdapUserDetails;
import it.govpay.core.autorizzazione.utils.AutorizzazioneUtils;
import it.govpay.core.beans.Costanti;
import it.govpay.core.beans.EsitoOperazione;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTO;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTOResponse;
import it.govpay.core.exceptions.GovPayException;
import it.govpay.core.exceptions.IOException;
import it.govpay.core.exceptions.ValidationException;
import it.govpay.core.utils.UriBuilderUtils;
import it.govpay.core.utils.rawutils.ConverterUtils;
import it.govpay.model.Utenza.TIPO_UTENZA;
import it.govpay.model.Versamento.TipologiaTipoVersamento;
import it.govpay.pagamento.v2.beans.NuovaPendenza;
import it.govpay.pagamento.v2.beans.NuovaVocePendenza;
import it.govpay.pagamento.v2.beans.NuovoPagamento;
import it.govpay.pagamento.v2.beans.PagamentoCreato;
import it.govpay.pagamento.v2.beans.QuotaContabilita;
import it.govpay.pagamento.v2.beans.Soggetto;
import it.govpay.pagamento.v2.beans.TassonomiaAvviso;
import it.govpay.pagamento.v2.beans.TipoSoggetto;
import it.govpay.rs.v1.authentication.SPIDAuthenticationDetailsSource;

public class PagamentiPortaleConverter {

	private PagamentiPortaleConverter() {}

	public static PagamentoCreato getPagamentiPortaleResponseOk(PagamentiPortaleDTOResponse dtoResponse) {
		PagamentoCreato  json = new PagamentoCreato();

		json.setId(dtoResponse.getId());
		json.setLocation(dtoResponse.getId().equals("0") ? "" : UriBuilderUtils.getFromPagamenti(dtoResponse.getId()));
		json.setRedirect(dtoResponse.getRedirectUrl());
		json.setIdSession(dtoResponse.getIdSessione());

		return json;
	}

	public static PagamentiPortaleDTO getPagamentiPortaleDTO(NuovoPagamento pagamentiPortaleRequest, Authentication user, String idSessione,
			String idSessionePortale, Map<String, Versamento> listaIdentificativi, Logger log) throws IOException, ValidationException, ServiceException, GovPayException {

		Map<String, Versamento> listaPendenzeDaSessione = null;
		PagamentiPortaleDTO pagamentiPortaleDTO = new PagamentiPortaleDTO(user);
		GovpayLdapUserDetails userDetails = AutorizzazioneUtils.getAuthenticationDetails(user);

		pagamentiPortaleDTO.setIdSessione(idSessione);
		pagamentiPortaleDTO.setIdSessionePortale(idSessionePortale);
		pagamentiPortaleDTO.setUrlRitorno(pagamentiPortaleRequest.getUrlRitorno());

		PagamentiPortaleConverter.controlloUtenzaVersante(pagamentiPortaleRequest, user);
		pagamentiPortaleDTO.setVersante(toAnagraficaCommons(pagamentiPortaleRequest.getSoggettoVersante()));

		if(pagamentiPortaleRequest.getPendenze() != null && !pagamentiPortaleRequest.getPendenze().isEmpty()) {
			List<Object> listRefs = new ArrayList<>();

			int i =0;
			for (NuovaPendenza pendenza: pagamentiPortaleRequest.getPendenze()) {

				if((pendenza.getIdDominio() != null && pendenza.getIdTipoPendenza() != null) && (pendenza.getIdA2A() == null && pendenza.getIdPendenza() == null && pendenza.getNumeroAvviso() == null)) {
					PagamentiPortaleDTO.RefVersamentoModello4 ref = pagamentiPortaleDTO. new RefVersamentoModello4();
					ref.setIdDominio(pendenza.getIdDominio());
					ref.setIdTipoPendenza(pendenza.getIdTipoPendenza());
					ref.setDati(ConverterUtils.toJSON(pendenza.getDati()));
					listRefs.add(ref);

				} else if((pendenza.getIdDominio() != null && pendenza.getNumeroAvviso() != null) && (pendenza.getIdA2A() == null && pendenza.getIdPendenza() == null)) {

					PagamentiPortaleDTO.RefVersamentoAvviso ref = pagamentiPortaleDTO. new RefVersamentoAvviso();
					ref.setIdDominio(pendenza.getIdDominio());
					ref.setNumeroAvviso(pendenza.getNumeroAvviso());
					ref.setIdDebitore(pendenza.getIdDebitore());
					listRefs.add(ref);

				} else	if((pendenza.getIdDominio() == null) && (pendenza.getIdA2A() != null && pendenza.getIdPendenza() != null)) {

					PagamentiPortaleDTO.RefVersamentoPendenza ref = pagamentiPortaleDTO. new RefVersamentoPendenza();
					ref.setIdA2A(pendenza.getIdA2A());
					ref.setIdPendenza(pendenza.getIdPendenza());
					listRefs.add(ref);

					// controllo se ci sono pendenze a disposizione in sessione
					if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.CITTADINO) || userDetails.getTipoUtenza().equals(TIPO_UTENZA.ANONIMO)) {
						if(listaIdentificativi != null && listaIdentificativi.size() > 0 && listaIdentificativi.containsKey((pendenza.getIdA2A()+pendenza.getIdPendenza()))) {
							if(listaPendenzeDaSessione == null) {
								listaPendenzeDaSessione = new HashMap<>();
							}

							// aggiungo la pendenza da pagare
							listaPendenzeDaSessione.put((pendenza.getIdA2A()+pendenza.getIdPendenza()), listaIdentificativi.get((pendenza.getIdA2A()+pendenza.getIdPendenza())));
							log.debug("Letta pendenza [idA2A:{}, idPendenza: {}] dalla sessione", pendenza.getIdA2A(), pendenza.getIdPendenza());
						}
					}

				}else if(pendenza.getIdA2A() != null && pendenza.getIdPendenza() != null && pendenza.getIdDominio() != null) {
					it.govpay.core.beans.commons.Versamento versamento = getVersamentoFromPendenza(pendenza);

					listRefs.add(versamento);
				} else {
					throw new ValidationException("La pendenza "+(i+1)+" e' di un tipo non riconosciuto.");
				}
				i++;
			}

			pagamentiPortaleDTO.setPendenzeOrPendenzeRef(listRefs);
		}

		pagamentiPortaleDTO.setListaPendenzeDaSessione(listaPendenzeDaSessione);

		return pagamentiPortaleDTO;
	}

	public static it.govpay.core.beans.commons.Versamento getVersamentoFromPendenza(NuovaPendenza pendenza) throws ValidationException, ServiceException, GovPayException, IOException {
		it.govpay.core.beans.commons.Versamento versamento = new it.govpay.core.beans.commons.Versamento();

		if(pendenza.getAnnoRiferimento() != null)
			versamento.setAnnoTributario(pendenza.getAnnoRiferimento().intValue());

		versamento.setCodLotto(pendenza.getCartellaPagamento());
		versamento.setCausale(pendenza.getCausale());
		versamento.setCodApplicazione(pendenza.getIdA2A());

		versamento.setCodDominio(pendenza.getIdDominio());
		versamento.setCodUnitaOperativa(pendenza.getIdUnitaOperativa());
		versamento.setCodVersamentoEnte(pendenza.getIdPendenza());
		versamento.setDataScadenza(pendenza.getDataScadenza());
		versamento.setDataValidita(pendenza.getDataValidita());
		versamento.setDataCaricamento(new Date());
		versamento.setDebitore(toAnagraficaCommons(pendenza.getSoggettoPagatore(), true));
		versamento.setImportoTotale(pendenza.getImporto());
		versamento.setTassonomia(pendenza.getTassonomia());
		if(pendenza.getTassonomiaAvviso() != null) {
			// valore tassonomia avviso non valido
			if(TassonomiaAvviso.fromValue(pendenza.getTassonomiaAvviso()) == null) {
				throw new ValidationException("Codifica inesistente per tassonomiaAvviso. Valore fornito [" + pendenza.getTassonomiaAvviso() + "] valori possibili " + ArrayUtils.toString(TassonomiaAvviso.values()));
			}

			versamento.setTassonomiaAvviso(pendenza.getTassonomiaAvviso());

		}
		versamento.setNumeroAvviso(pendenza.getNumeroAvviso());
		versamento.setCartellaPagamento(pendenza.getCartellaPagamento());

		if(pendenza.getDatiAllegati() != null)
			versamento.setDatiAllegati(ConverterUtils.toJSON(pendenza.getDatiAllegati()));

		// voci pagamento
		fillSingoliVersamentiFromVociPendenza(versamento, pendenza.getVoci());

		// tipo Pendenza
		versamento.setCodTipoVersamento(pendenza.getIdTipoPendenza());
		versamento.setDirezione(pendenza.getDirezione());
		versamento.setDivisione(pendenza.getDivisione());

		// tipo versamento e' deciso dall'api che lo carica.
		versamento.setTipo(TipologiaTipoVersamento.SPONTANEO);

		versamento.setProprieta(PendenzeConverter.toProprietaPendenzaDTO(pendenza.getProprieta()));

		versamento.setAllegati(PendenzeConverter.toAllegatiPendenzaDTO(pendenza.getAllegati()));

		return versamento;
	}


	public static void fillSingoliVersamentiFromVociPendenza(it.govpay.core.beans.commons.Versamento versamento, List<NuovaVocePendenza> voci) throws ServiceException, GovPayException, IOException {

		if(voci != null && !voci.isEmpty()) {
			for (NuovaVocePendenza vocePendenza : voci) {
				it.govpay.core.beans.commons.Versamento.SingoloVersamento sv = new it.govpay.core.beans.commons.Versamento.SingoloVersamento();

				sv.setCodSingoloVersamentoEnte(vocePendenza.getIdVocePendenza());
				if(vocePendenza.getDatiAllegati() != null)
					sv.setDatiAllegati(ConverterUtils.toJSON(vocePendenza.getDatiAllegati()));

				sv.setDescrizione(vocePendenza.getDescrizione());
				sv.setDescrizioneCausaleRPT(vocePendenza.getDescrizioneCausaleRPT());
				sv.setImporto(vocePendenza.getImporto());
				sv.setCodDominio(vocePendenza.getIdDominio());
				sv.setMetadata(PendenzeConverter.toMetadataDTO(vocePendenza.getMetadata()));

				// Definisce i dati di un bollo telematico
				if(vocePendenza.getHashDocumento() != null && vocePendenza.getTipoBollo() != null && vocePendenza.getProvinciaResidenza() != null) {
					it.govpay.core.beans.commons.Versamento.SingoloVersamento.BolloTelematico bollo = new it.govpay.core.beans.commons.Versamento.SingoloVersamento.BolloTelematico();
					bollo.setHash(vocePendenza.getHashDocumento());
					bollo.setProvincia(vocePendenza.getProvinciaResidenza());
					bollo.setTipo(vocePendenza.getTipoBollo().getCodifica());
					sv.setBolloTelematico(bollo);
				} else if(vocePendenza.getCodEntrata() != null) { // Definisce i dettagli di incasso tramite riferimento in anagrafica GovPay.
					sv.setCodTributo(vocePendenza.getCodEntrata());

				} else { // Definisce i dettagli di incasso della singola entrata.
					it.govpay.core.beans.commons.Versamento.SingoloVersamento.Tributo tributo = new it.govpay.core.beans.commons.Versamento.SingoloVersamento.Tributo();
					tributo.setCodContabilita(vocePendenza.getCodiceContabilita());
					tributo.setIbanAccredito(vocePendenza.getIbanAccredito());
					tributo.setIbanAppoggio(vocePendenza.getIbanAppoggio());
					tributo.setTipoContabilita(it.govpay.core.beans.commons.Versamento.SingoloVersamento.TipoContabilita.valueOf(vocePendenza.getTipoContabilita().name()));
					sv.setTributo(tributo);
				}
				sv.setContabilita(ContabilitaConverter.toStringDTO(vocePendenza.getContabilita()));

				if(vocePendenza.getContabilita() != null) {
					if(vocePendenza.getContabilita().getQuote() != null) {
						BigDecimal somma = BigDecimal.ZERO;
						for (QuotaContabilita voceContabilita : vocePendenza.getContabilita().getQuote()) {
							somma = somma.add(voceContabilita.getImporto());
						}

						if(somma.compareTo(vocePendenza.getImporto()) != 0) {
							throw new GovPayException(EsitoOperazione.VER_035, vocePendenza.getIdVocePendenza(),  versamento.getCodApplicazione(), versamento.getCodVersamentoEnte(),
								Double.toString(sv.getImporto().doubleValue()), Double.toString(somma.doubleValue()));
						}
					}
				}

				versamento.getSingoloVersamento().add(sv);
			}
		}
	}

	public static it.govpay.core.beans.commons.Anagrafica toAnagraficaCommons(Soggetto anagraficaRest) {
		return toAnagraficaCommons(anagraficaRest, false);
	}

	public static it.govpay.core.beans.commons.Anagrafica toAnagraficaCommons(Soggetto anagraficaRest, boolean createAnagraficaAnonimo) {
		it.govpay.core.beans.commons.Anagrafica anagraficaCommons = null;
		if(anagraficaRest != null) {
			anagraficaCommons = new it.govpay.core.beans.commons.Anagrafica();
			anagraficaCommons.setCap(anagraficaRest.getCap());
			anagraficaCommons.setCellulare(anagraficaRest.getCellulare());
			anagraficaCommons.setCivico(anagraficaRest.getCivico());
			anagraficaCommons.setCodUnivoco(anagraficaRest.getIdentificativo());
			anagraficaCommons.setEmail(anagraficaRest.getEmail());
			anagraficaCommons.setIndirizzo(anagraficaRest.getIndirizzo());
			anagraficaCommons.setLocalita(anagraficaRest.getLocalita());
			anagraficaCommons.setNazione(anagraficaRest.getNazione());
			anagraficaCommons.setProvincia(anagraficaRest.getProvincia());
			anagraficaCommons.setRagioneSociale(anagraficaRest.getAnagrafica());
			if(anagraficaRest.getTipo() != null) {
				anagraficaCommons.setTipo(anagraficaRest.getTipo().name());
			}
		}

		if(createAnagraficaAnonimo) {
			if(anagraficaCommons == null) {
				anagraficaCommons = new it.govpay.core.beans.commons.Anagrafica();
			}

			// Il vincolo di obbligatorieta' del soggetto pagatore e' stato eliminato per consentire di acquisire pendenze senza indicare il debitore.
			// in questo caso impostiamo i valori di default per gli identificativi
			if(StringUtils.isBlank(anagraficaCommons.getCodUnivoco())) {
				anagraficaCommons.setCodUnivoco(Costanti.IDENTIFICATIVO_DEBITORE_ANONIMO);
			}
			if(StringUtils.isBlank(anagraficaCommons.getRagioneSociale())) {
				anagraficaCommons.setRagioneSociale(Costanti.IDENTIFICATIVO_DEBITORE_ANONIMO);
			}
		}

		return anagraficaCommons;
	}

	public static void controlloUtenzaVersante(NuovoPagamento pagamentoPost, Authentication user) throws ValidationException {
		Soggetto versante = pagamentoPost.getSoggettoVersante();

		GovpayLdapUserDetails userDetails = AutorizzazioneUtils.getAuthenticationDetails(user);


		if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.CITTADINO)) {
			if(versante == null) {
				versante = new Soggetto();
				pagamentoPost.setSoggettoVersante(versante);
			}

			UtenzaCittadino cittadino = (UtenzaCittadino) userDetails.getUtenza();
			versante.setIdentificativo(cittadino.getCodIdentificativo());
			String nomeCognome = cittadino.getProprieta(SPIDAuthenticationDetailsSource.SPID_HEADER_NAME) + " "
					+ cittadino.getProprieta(SPIDAuthenticationDetailsSource.SPID_HEADER_FAMILY_NAME);
			versante.setAnagrafica(nomeCognome);
			if(versante.getEmail() == null &&  cittadino.getProprieta(SPIDAuthenticationDetailsSource.SPID_HEADER_EMAIL) != null)
				versante.setEmail(cittadino.getProprieta(SPIDAuthenticationDetailsSource.SPID_HEADER_EMAIL));
			versante.setTipo(TipoSoggetto.F);
			versante.setCap(null);
			versante.setCellulare(null);
			versante.setCivico(null);
			versante.setIndirizzo(null);
			versante.setLocalita(null);
			versante.setNazione(null);
			versante.setProvincia(null);
		}

		if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.ANONIMO)) {
			if(versante == null) {
				versante = new Soggetto();
				pagamentoPost.setSoggettoVersante(versante);
			}

			versante.setIdentificativo(TIPO_UTENZA.ANONIMO.toString());
			versante.setAnagrafica(TIPO_UTENZA.ANONIMO.toString());
			versante.setTipo(TipoSoggetto.F);
			versante.setCap(null);
			versante.setCellulare(null);
			versante.setCivico(null);
			versante.setIndirizzo(null);
			versante.setLocalita(null);
			versante.setNazione(null);
			versante.setProvincia(null);

			if(StringUtils.isEmpty(versante.getEmail()))
				throw new ValidationException("Il campo email del soggetto versante e' obbligatorio.");
		}
	}


}
