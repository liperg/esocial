package br.jus.tst.esocialjt.xml;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.jus.tst.esocialjt.util.Sequencial;

@Component
public class GeradorId {

	@Autowired
	private Sequencial sequencial;

	public String gerarId(String cnpj) {
		LocalDateTime date = LocalDateTime.now();
		String seq = String.format("%05d", sequencial.proximo());
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

		// caso seja o CNPJ base acrescenta zeros para completar o tamanho
		if (cnpj.length() == 8) {
			cnpj = cnpj + "000000";
		}

		return "ID1" + cnpj + date.format(formatter).replace("+", "") + seq;
		//return "ID1337490860001092022021406303900001";
	}

}
