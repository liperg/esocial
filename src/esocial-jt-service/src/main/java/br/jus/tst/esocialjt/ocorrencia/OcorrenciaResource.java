package br.jus.tst.esocialjt.ocorrencia;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

import br.jus.tst.esocial.ocorrencia.OcorrenciaDTO;
import br.jus.tst.esocial.ocorrencia.Operacao;
import br.jus.tst.esocial.ocorrencia.TipoOcorrencia;
import br.jus.tst.esocialjt.dominio.Estado;
import br.jus.tst.esocialjt.dominio.Ocorrencia;
import br.jus.tst.esocialjt.dominio.TipoEvento;
import br.jus.tst.esocialjt.negocio.exception.EntidadeNaoExisteException;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/ocorrencias")
public class OcorrenciaResource {

	@Autowired
	private OcorrenciaServico ocorrenciaServico;

	@Autowired
	private ExemploOcorrenciaServico exemploServico;

	@Operation(summary ="Consulta todas as ocorrências já recebidas pelo sistema, exibindo informações completas.")
	@GetMapping
	@Deprecated
	public List<Ocorrencia> listarTodos() {
		return ocorrenciaServico.recuperaTodos();
	}

	@Operation(summary ="Consulta todas as ocorrências já recebidas pelo sistema, exibindo informações simplificadas. Esta consulta tende a ser mais rápida que a consulta de dados completos.")
	@GetMapping("/dados-basicos")
	@Deprecated
	public List<OcorrenciaDadosBasicosDTO> listarDadosBasicos() {
		return ocorrenciaServico.recuperaDadosBasicos();
	}

	@GetMapping(value="/sumario/{tipo}", produces = "text/csv")
	@Operation(summary = "Retorna um resumo de todos eventos enviados para um tipo em csv. Pode ser muito lento para um grande volume de dados.")
	public void getSumario(HttpServletResponse response, @PathVariable("tipo") long tipo) throws IOException{
		response.setContentType("text/csv");

		String[] params = {"id", "cpf", "matricula", "referencia", "tipo", "estado", "dataOcorrencia"};
		List<OcorrenciaSumario> sumario = ocorrenciaServico.getSumario(new TipoEvento(tipo));

		CsvBeanWriter csvWriter = new CsvBeanWriter(response.getWriter(), CsvPreference.STANDARD_PREFERENCE);
		csvWriter.writeHeader(params);
		for (OcorrenciaSumario s : sumario) {
			csvWriter.write(s, params);
		}
 		csvWriter.close();
	}

	@Operation(summary = "Consulta, com paginação, todas as ocorrências já recebidas pelo sistema, exibindo informações completas.")
	@GetMapping("/paginado")
	public OcorrenciaPage listarPaginado(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false, defaultValue = "") List<Long> estados,
			@RequestParam(required = false, defaultValue = "") String expressao,
			@RequestParam(required = false, defaultValue = "") List<Long> tipos,
			@RequestParam(required = false, defaultValue = "false") boolean incluirArquivados,
			@RequestParam(required = false, defaultValue = "") String cpf) {
		List<Estado> estadosObj = estados
									.stream()
									.map(Estado::new)
									.collect(Collectors.toList());
		
		List<TipoEvento> tiposObj = tipos
									.stream()
									.map(TipoEvento::new)
									.collect(Collectors.toList());
		
		return ocorrenciaServico.recuperaPaginado(page, size, estadosObj, expressao, tiposObj, incluirArquivados, cpf);
	}
	
	@Operation(summary = "Consulta os tipos já enviados para o esocial.")
	@GetMapping("/tipos")
	public List<Long> listarTiposExistentes() {
		return ocorrenciaServico.buscarTiposEnviados();
	}

	@Operation(summary ="Consulta uma ocorrência especificada pelo id.")
	@GetMapping("/{id}")
	public Ocorrencia getOcorrenciaPorId(@PathVariable("id") long id) throws EntidadeNaoExisteException {
		return ocorrenciaServico.recuperaPorId(id);
	}
	
	@Operation(summary = "Url única para o recebimento de ocorrências. O tipo da ocorrência é passado no próprio json dos dados e deve obedecer "
			+ "ao formato respectivo àquele tipo.")
	@PostMapping(consumes = "application/json", produces = "application/json;charset=UTF-8")
	public Ocorrencia receber(@RequestBody OcorrenciaDTO ocorrenciaDTO) {
		Ocorrencia ocorrencia = OcorrenciaMapper.INSTANCE.comoOcorrencia(ocorrenciaDTO);
		return ocorrenciaServico.salvar(ocorrencia);
	}
	
	@Operation(summary = "Url única para o recebimento de ocorrências. O tipo da ocorrência é passado no próprio json dos dados e deve obedecer "
			+ "ao formato respectivo àquele tipo.")
	@PostMapping(value = "/as/xml", consumes = "application/xml", produces = "application/json;charset=UTF-8")
	public Ocorrencia receberAsXml(@RequestBody String xml) {
		TipoOcorrencia tipoOcorrencia = tipoOcorrenciaFromXml(xml);
		if(tipoOcorrencia == null)
			throw new RuntimeException("Não foi possível determinar o tipo de ocorrência através do XML");
		
		OcorrenciaDTO dto = new OcorrenciaDTO();
		dto.setDataOcorrencia(new Date());
		dto.setOperacao(Operacao.NORMAL);
		dto.setReferencia(UUID.randomUUID().toString());
		dto.setTipoOcorrencia(tipoOcorrencia);
		Ocorrencia ocorrencia = OcorrenciaMapper.INSTANCE.comoOcorrencia(dto);
		ocorrencia.setTxtDadosOcorrenciaAsXml(xml);
		
		return ocorrenciaServico.salvarAsXml(ocorrencia);
	}
	
	private TipoOcorrencia tipoOcorrenciaFromXml(String xml) {
		Pattern pattern = Pattern.compile("(?<=\\/evt\\/)(.*)(?=\\/)");
        Matcher matcher = pattern.matcher(xml);
        if(!matcher.find())
        	return null;
        String evt = matcher.group().substring(3).toLowerCase();
        if("infoempregador".equalsIgnoreCase(evt)) {
        	return TipoOcorrencia.INFORMACOES_EMPREGADOR;
        }
        if("admissao".equalsIgnoreCase(evt)) {
        	return TipoOcorrencia.ADMISSAO_TRABALHADOR;
        }
        if("remun".equalsIgnoreCase(evt)) {
        	return TipoOcorrencia.REMUNERACAO_RGPS;
        }
        if("rmnrpps".equalsIgnoreCase(evt)) {
        	return TipoOcorrencia.REMUNERACAO_RPPS;
        }
        if("pgtos".equalsIgnoreCase(evt)) {
        	return TipoOcorrencia.PAGAMENTOS;
        }
        return Arrays.stream(TipoOcorrencia.values())
        	.filter(v -> v.toString().toLowerCase().replace("_", "").equalsIgnoreCase(evt))
        	.findFirst().orElse(null);
	}

	@Operation(summary ="Lista URLs com exemplos disponíveis para como uma ocorrência deve ser enviada para o /esocial-jt-service/ocorrencias.")
	@GetMapping("/exemplos")
	public List<String> getExemplo() {
		return Arrays.stream(TipoOcorrencia.values())
			.map(tipo -> "/ocorrencias/exemplos/" + tipo.toString())
			.collect(Collectors.toList());
	}
	
	@Operation(summary ="Retorna o modelo de JSON para cada tipo de ocorrência que deve ser enviada para o /esocial-jt-service/ocorrencias.")
	@GetMapping("/exemplos/{tipo}")
	public OcorrenciaDTO getExemplosPorTipo(@PathVariable("tipo") TipoOcorrencia tipo) 
			throws EntidadeNaoExisteException, IOException {
		return exemploServico.lerOcorrenciaDTO(tipo);
	}
	
	@Operation(summary ="Arquivar uma ocorrência especificada pelo id.")
	@PostMapping("/{id}/acoes/arquivar")
	public int arquivarOcorrenciaPorId(@PathVariable("id") long id) {
		return ocorrenciaServico.arquivar(id);
	}
	
	@Operation(summary ="Desarquivar uma ocorrência especificada pelo id.")
	@PostMapping("/{id}/acoes/desarquivar")
	public int desarquivarOcorrenciaPorId(@PathVariable("id") long id) {
		return ocorrenciaServico.desarquivar(id);
	}

}
