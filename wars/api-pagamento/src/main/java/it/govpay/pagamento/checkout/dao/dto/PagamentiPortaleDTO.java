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
package it.govpay.pagamento.checkout.dao.dto;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedMap;

import org.springframework.security.core.Authentication;

import it.govpay.bd.model.Versamento;
import it.govpay.core.beans.commons.Anagrafica;
import it.govpay.core.dao.anagrafica.dto.BasicCreateRequestDTO;

public class PagamentiPortaleDTO  extends BasicCreateRequestDTO{

	public PagamentiPortaleDTO(Authentication user) {
		super(user);
	}

	private String idSessione = null;
	private String idSessionePortale =null;
	private String urlRitorno = null;
	private Anagrafica versante = null;
	private List<Object> pendenzeOrPendenzeRef = null;
	private MultivaluedMap<String, String> queryParameters;
	private MultivaluedMap<String, String> pathParameters;
	private Map<String, String> headers;
	private Map<String,Versamento> listaPendenzeDaSessione = null;

	public String getUrlRitorno() {
		return this.urlRitorno;
	}
	public void setUrlRitorno(String urlRitorno) {
		this.urlRitorno = urlRitorno;
	}
	public Anagrafica getVersante() {
		return this.versante;
	}
	public void setVersante(Anagrafica versante) {
		this.versante = versante;
	}
	public List<Object> getPendenzeOrPendenzeRef() {
		return this.pendenzeOrPendenzeRef;
	}
	public void setPendenzeOrPendenzeRef(List<Object> pendenzeOrPendenzeRef) {
		this.pendenzeOrPendenzeRef = pendenzeOrPendenzeRef;
	}
	public String getIdSessione() {
		return this.idSessione;
	}
	public void setIdSessione(String idSessione) {
		this.idSessione = idSessione;
	}
	public String getIdSessionePortale() {
		return this.idSessionePortale;
	}
	public void setIdSessionePortale(String idSessionePortale) {
		this.idSessionePortale = idSessionePortale;
	}
	public MultivaluedMap<String, String> getQueryParameters() {
		return this.queryParameters;
	}
	public void setQueryParameters(MultivaluedMap<String, String> queryParameters) {
		this.queryParameters = queryParameters;
	}
	public MultivaluedMap<String, String> getPathParameters() {
		return this.pathParameters;
	}
	public void setPathParameters(MultivaluedMap<String, String> pathParameters) {
		this.pathParameters = pathParameters;
	}
	public Map<String, String> getHeaders() {
		return this.headers;
	}
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}
	public Map<String, Versamento> getListaPendenzeDaSessione() {
		return this.listaPendenzeDaSessione;
	}
	public void setListaPendenzeDaSessione(Map<String, Versamento> listaPendenzeDaSessione) {
		this.listaPendenzeDaSessione = listaPendenzeDaSessione;
	}

	public class RefVersamentoAvviso {

		private String idDominio;
		private String numeroAvviso;
		private String idDebitore;
		
		public String getIdDominio() {
			return this.idDominio;
		}
		public void setIdDominio(String idDominio) {
			this.idDominio = idDominio;
		}
		public String getNumeroAvviso() {
			return this.numeroAvviso;
		}
		public void setNumeroAvviso(String numeroAvviso) {
			this.numeroAvviso = numeroAvviso;
		}
		public String getIdDebitore() {
			return idDebitore;
		}
		public void setIdDebitore(String idDebitore) {
			this.idDebitore = idDebitore;
		}
	}

	public class RefVersamentoPendenza {

		private String idA2A;
		private String idPendenza;
		public String getIdA2A() {
			return this.idA2A;
		}
		public void setIdA2A(String idA2A) {
			this.idA2A = idA2A;
		}
		public String getIdPendenza() {
			return this.idPendenza;
		}
		public void setIdPendenza(String idPendenza) {
			this.idPendenza = idPendenza;
		}
	}

	public class RefVersamentoModello4 {

		private String idDominio;
		private String idTipoPendenza;
		private String dati;
		public String getIdDominio() {
			return idDominio;
		}
		public void setIdDominio(String idDominio) {
			this.idDominio = idDominio;
		}
		public String getIdTipoPendenza() {
			return idTipoPendenza;
		}
		public void setIdTipoPendenza(String idTipoPendenza) {
			this.idTipoPendenza = idTipoPendenza;
		}
		public String getDati() {
			return dati;
		}
		public void setDati(String dati) {
			this.dati = dati;
		}

	}
}
