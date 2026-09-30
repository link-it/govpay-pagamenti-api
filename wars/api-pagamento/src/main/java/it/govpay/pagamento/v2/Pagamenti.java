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
package it.govpay.pagamento.v2;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import it.govpay.pagamento.v2.controller.PagamentiController;
import it.govpay.rs.v2.BaseRsServiceV2;

@Path("/pagamenti")

public class Pagamenti extends BaseRsServiceV2{


	private PagamentiController controller = null;

	public Pagamenti() {
		super("pagamenti");
		this.controller = new PagamentiController(this.nomeServizio,this.log);
	}



    @POST
    @Path("/")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    public Response addPagamento(@Context UriInfo uriInfo,
    		@Context HttpHeaders httpHeaders,
    		java.io.InputStream is,
    		@QueryParam("idSessionePortale") String idSessionePortale){
        this.buildContext();
        this.controller.setRequestResponse(this.request, this.response);

        return this.controller.addPagamento(this.getUser(),
        		uriInfo,
        		httpHeaders,
        		is,
        		idSessionePortale);
    }

}
