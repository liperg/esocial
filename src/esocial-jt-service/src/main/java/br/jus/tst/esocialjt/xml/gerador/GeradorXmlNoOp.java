package br.jus.tst.esocialjt.xml.gerador;

import org.springframework.stereotype.Component;

import br.jus.tst.esocialjt.dominio.Evento;
import br.jus.tst.esocialjt.negocio.exception.GeracaoXmlException;

@Component
public class GeradorXmlNoOp extends GeradorXml {

	@Override
	public String gerarXml(Evento evento) throws GeracaoXmlException {
		String xml = evento.getOcorrencia().getTxtDadosOcorrencia();
		return getAssinadorXml().assinar(xml);
	}

	@Override
	public String getArquivoXSD() {
		return null;
	}

	@Override
	public Object criarObjetoESocial(Evento evento) throws GeracaoXmlException {
		return null;
	}

}
