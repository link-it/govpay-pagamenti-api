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

import java.util.ArrayList;
import java.util.List;

import it.govpay.bd.model.Versamento;
import it.govpay.pagamento.v2.beans.AllegatoPendenza;
import it.govpay.pagamento.v2.beans.LinguaSecondaria;
import it.govpay.pagamento.v2.beans.MapEntry;
import it.govpay.pagamento.v2.beans.Metadata;
import it.govpay.pagamento.v2.beans.NuovoAllegatoPendenza;
import it.govpay.pagamento.v2.beans.ProprietaPendenza;
import it.govpay.pagamento.v2.beans.VoceDescrizioneImporto;

@Deprecated(since = "3.9.0", forRemoval = true)
public class PendenzeConverter {

	public static it.govpay.core.beans.tracciati.ProprietaPendenza toProprietaPendenzaDTO(ProprietaPendenza proprieta) {
		it.govpay.core.beans.tracciati.ProprietaPendenza dto = null;
		if(proprieta != null) {
			dto = new it.govpay.core.beans.tracciati.ProprietaPendenza();

			if(proprieta.getDescrizioneImporto() != null && !proprieta.getDescrizioneImporto().isEmpty()) {
				List<it.govpay.core.beans.tracciati.VoceDescrizioneImporto> descrizioneImporto = new ArrayList<>();
				for (VoceDescrizioneImporto vdI : proprieta.getDescrizioneImporto()) {
					it.govpay.core.beans.tracciati.VoceDescrizioneImporto voce = new it.govpay.core.beans.tracciati.VoceDescrizioneImporto();

					voce.setVoce(vdI.getVoce());
					voce.setImporto(vdI.getImporto());

					descrizioneImporto.add(voce);
				}
				dto.setDescrizioneImporto(descrizioneImporto);
			}
			dto.setLineaTestoRicevuta1(proprieta.getLineaTestoRicevuta1());
			dto.setLineaTestoRicevuta2(proprieta.getLineaTestoRicevuta2());
			if(proprieta.getLinguaSecondaria() != null) {
				switch(LinguaSecondaria.fromValue(proprieta.getLinguaSecondaria())) {
				case DE:
					dto.setLinguaSecondaria(it.govpay.core.beans.tracciati.LinguaSecondaria.DE);
					break;
				case EN:
					dto.setLinguaSecondaria(it.govpay.core.beans.tracciati.LinguaSecondaria.EN);
					break;
				case FALSE:
					dto.setLinguaSecondaria(it.govpay.core.beans.tracciati.LinguaSecondaria.FALSE);
					break;
				case FR:
					dto.setLinguaSecondaria(it.govpay.core.beans.tracciati.LinguaSecondaria.FR);
					break;
				case SL:
					dto.setLinguaSecondaria(it.govpay.core.beans.tracciati.LinguaSecondaria.SL);
					break;
				}
			}
			dto.setLinguaSecondariaCausale(proprieta.getLinguaSecondariaCausale());
			dto.setInformativaImportoAvviso(proprieta.getInformativaImportoAvviso());
			dto.setLinguaSecondariaInformativaImportoAvviso(proprieta.getLinguaSecondariaInformativaImportoAvviso());
			dto.setDataScandenzaAvviso(proprieta.getDataScandenzaAvviso());
		}

		return dto;
	}

	public static List<it.govpay.core.beans.commons.Versamento.AllegatoPendenza> toAllegatiPendenzaDTO(List<NuovoAllegatoPendenza> allegati) {
		List<it.govpay.core.beans.commons.Versamento.AllegatoPendenza> allegatiDTO = null;

		if(allegati != null && allegati.size() > 0) {
			allegatiDTO = new ArrayList<>();

			for (NuovoAllegatoPendenza allegato : allegati) {
				it.govpay.core.beans.commons.Versamento.AllegatoPendenza allegatoDTO = new it.govpay.core.beans.commons.Versamento.AllegatoPendenza();

				allegatoDTO.setNome(allegato.getNome());
				allegatoDTO.setTipo(allegato.getTipo());
				allegatoDTO.setDescrizione(allegato.getDescrizione());
				allegatoDTO.setContenuto(allegato.getContenuto());

				allegatiDTO.add(allegatoDTO);
			}
		}

		return allegatiDTO;
	}

	public static it.govpay.core.beans.tracciati.Metadata toMetadataDTO(Metadata metadata) {
		it.govpay.core.beans.tracciati.Metadata dto = null;
		if(metadata != null) {
			dto = new it.govpay.core.beans.tracciati.Metadata();

			if(metadata.getMapEntries() != null && !metadata.getMapEntries().isEmpty()) {
				List<it.govpay.core.beans.tracciati.MapEntry> mapEntriesDto = new ArrayList<>();

				for (MapEntry mapEntry : metadata.getMapEntries()) {
					it.govpay.core.beans.tracciati.MapEntry mapEntryDto = new it.govpay.core.beans.tracciati.MapEntry();
					mapEntryDto.setKey(mapEntry.getKey());
					mapEntryDto.setValue(mapEntry.getValue());

					mapEntriesDto.add(mapEntryDto);
				}

				dto.setMapEntries(mapEntriesDto);
			}
		}

		return dto;
	}

}
