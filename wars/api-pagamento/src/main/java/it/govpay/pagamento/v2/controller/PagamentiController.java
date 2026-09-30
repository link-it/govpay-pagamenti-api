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
package it.govpay.pagamento.v2.controller;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openspcoop2.utils.service.context.ContextThreadLocal;
import org.slf4j.Logger;
import org.springframework.security.core.Authentication;

import it.govpay.bd.model.Versamento;
import it.govpay.core.autorizzazione.beans.GovpayLdapUserDetails;
import it.govpay.core.autorizzazione.utils.AutorizzazioneUtils;
import it.govpay.core.beans.JSONSerializable;
import it.govpay.pagamento.checkout.dao.PagamentiPortaleDAO;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTO;
import it.govpay.pagamento.checkout.dao.dto.PagamentiPortaleDTOResponse;
import it.govpay.model.Acl.Diritti;
import it.govpay.model.Acl.Servizio;
import it.govpay.model.Utenza.TIPO_UTENZA;
import it.govpay.pagamento.v2.beans.NuovoPagamento;
import it.govpay.pagamento.v2.beans.PagamentoCreato;
import it.govpay.pagamento.v2.beans.converter.PagamentiPortaleConverter;

public class PagamentiController extends BaseController {

     public PagamentiController(String nomeServizio,Logger log) {
		super(nomeServizio,log);
     }


    @SuppressWarnings("unchecked")
	public Response addPagamento(Authentication user,
			UriInfo uriInfo,
			HttpHeaders httpHeaders,
			java.io.InputStream is,
			String idSessionePortale) {
    	String methodName = "pagamentiPOST";
		String transactionId = ContextThreadLocal.get().getTransactionId();
		this.logDebug(BaseController.LOG_MSG_ESECUZIONE_METODO_IN_CORSO, methodName);
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream();){
			// salvo il json ricevuto
			IOUtils.copy(is, baos);

			// autorizzazione sulla API
			this.isAuthorized(user, Arrays.asList(TIPO_UTENZA.ANONIMO, TIPO_UTENZA.CITTADINO, TIPO_UTENZA.APPLICAZIONE), Arrays.asList(Servizio.API_PAGAMENTI), Arrays.asList(Diritti.SCRITTURA));

			String jsonRequest = baos.toString();
			NuovoPagamento pagamentiPortaleRequest= JSONSerializable.parse(jsonRequest, NuovoPagamento.class);
			pagamentiPortaleRequest.validate();

			GovpayLdapUserDetails userDetails = AutorizzazioneUtils.getAuthenticationDetails(user);
			Map<String, Versamento> listaIdentificativi = null;
			if(userDetails.getTipoUtenza().equals(TIPO_UTENZA.CITTADINO) || userDetails.getTipoUtenza().equals(TIPO_UTENZA.ANONIMO)) {
				 HttpSession session = this.request.getSession(false);
				 if(session!= null) {
					 listaIdentificativi = (Map<String, Versamento>) session.getAttribute(BaseController.PENDENZE_CITTADINO_ATTRIBUTE);
					 log.debug("Letta lista degli identificativi pendenza dalla sessione con id ["+session.getId()+"]");
					 log.debug("Identificativi pendenza associati all'utenza: ["
					 + ((listaIdentificativi != null && listaIdentificativi.size() > 0) ? StringUtils.join(listaIdentificativi.keySet(), ",") : "") +"]");
				 }
			}

			String idSession = transactionId.replace("-", "");
			PagamentiPortaleDTO pagamentiPortaleDTO = PagamentiPortaleConverter.getPagamentiPortaleDTO(pagamentiPortaleRequest, user,idSession, idSessionePortale, listaIdentificativi, this.log);

			pagamentiPortaleDTO.setHeaders(this.getHeaders(getRequest()));
			pagamentiPortaleDTO.setPathParameters(uriInfo.getPathParameters());
			pagamentiPortaleDTO.setQueryParameters(uriInfo.getQueryParameters());

			PagamentiPortaleDAO pagamentiPortaleDAO = new PagamentiPortaleDAO();

			PagamentiPortaleDTOResponse pagamentiPortaleDTOResponse = pagamentiPortaleDAO.inserisciPagamenti(pagamentiPortaleDTO);

			if(!pagamentiPortaleDTOResponse.isRedirect()) {
				PagamentoCreato responseOk = PagamentiPortaleConverter.getPagamentiPortaleResponseOk(pagamentiPortaleDTOResponse);

				this.logDebug(BaseController.LOG_MSG_ESECUZIONE_METODO_COMPLETATA, methodName);
				return this.handleResponseOk(Response.status(Status.CREATED).entity(responseOk.toJSON(null)),transactionId).build();
			} else {
				this.logDebug(BaseController.LOG_MSG_ESECUZIONE_METODO_COMPLETATA, methodName);
				// la specifica dice che deve essere inviato uno status 302
				return this.handleResponseOk(Response.status(Status.FOUND).location(new URI(pagamentiPortaleDTOResponse.getLocation())),transactionId).build();
			}
		} catch (Exception e) {
			return this.handleException(uriInfo, httpHeaders, methodName, e,transactionId);
		} finally {
			this.logContext(ContextThreadLocal.get());
		}
    }
}
