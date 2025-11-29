# WordCount — benchmark: Serial / CPU / GPU

Uma ferramenta pequena para comparar contagem de ocorrências de uma palavra em textos usando três abordagens:

- Serial CPU (`WordCountMain.countSerial`) — busca simples, permite matches sobrepostos.
- Paralelo CPU (`WordCountMain.countParallelCPU`) — divide linhas por *chunks* e usa um pool de threads.
- Paralelo GPU (`GPUWordCounter.countWithJOCL`) — usa JOCL/OpenCL; cada índice é testado no kernel.

— Estrutura do projeto (detalhada)

Este projeto está organizado para separar responsabilidades: código Java (contagem e GPU), kernel OpenCL, utilitários de benchmark, e frontend Python para visualização.

- `src/`
	- `WordCountMain.java` — ponto de entrada CLI. Contém as três estratégias (serial, paralelo CPU e invocação do GPU). Responsável por:
		- parse de argumentos e execução das rotinas de benchmark;
		- repetir execuções (`runs`) e gravar resultados em CSV (`method,file,target,count,millis,run`);
		- escolher entre modos `serial`, `cpu`, `gpu` e `all`.
	- `GPUWordCounter.java` — implementação JOCL/OpenCL que prepara buffers, compila o kernel (ou carrega `kernels/match_kernel.cl`) e executa a kernel. Observações de design:
		- JOCL é usado apenas quando o JAR e as libs nativas estiverem disponíveis; o código tenta falhar graciosamente para permitir execuções apenas-CPU.
		- a kernel faz comparação byte-a-byte (UTF-8); por isso strings multi-byte precisam ser tratadas com cuidado.

- `kernels/`
	- `match_kernel.cl` — kernel OpenCL que avalia se o `target` aparece começando em cada índice do texto. O kernel é simples por legibilidade; para produção considere redução por work-group para contar ocorrências eficientemente.

- `python-frontend/`
	- `plot_results.py` — script para gerar gráficos (tempo, speedup e contagens) a partir de `results/results.csv`.
		- Usa `pandas` para agregar e `matplotlib` para desenhar gráficos agrupados e anotar barras.
		- Projetado para ser executado em um venv Python (veja `requirements.txt`).

- `samples/`
	- Contém textos de exemplo (e.g., `MobyDick-217452.txt`). O `benchmark.ps1` cria partições (25/50/75/100%) desses arquivos para testar escala de entrada.

- `results/`
	- Local onde o `benchmark.ps1` e `WordCountMain` escrevem CSVs e onde o plotter salva PNGs. Este diretório é ignorado por `.gitignore` para evitar commitar resultados grandes.

- `libs/`
	- Local sugerido para colocar `jocl-2.0.4.jar` (não comitado por padrão). As libs nativas (DLL/.so/.dylib) normalmente não ficam no repositório — instruções para adicioná-las localmente estão no `USER_MANUAL.md`.

- `benchmark.ps1` (PowerShell)
	- Orquestra compilação opcional, particionamento dos samples, execução de `WordCountMain` nos vários modos, e consolidação dos CSVs em `results/results.csv`.
	- Projetado para ser idempotente e para warn/skip o modo GPU quando JOCL não estiver presente.

- Arquivos de suporte
	- `requirements.txt` — dependências Python (`pandas`, `matplotlib`).
	- `.gitignore` — regras para não commitar `results/`, `*.class`, ambientes virtuais, e libs nativas.
	- `USER_MANUAL.md` — instruções passo-a-passo para configurar o ambiente e executar benchmarks em Windows (PowerShell).

Escolhas de design e razões

- Separação de responsabilidades: mantive a lógica de contagem em Java (para testes de desempenho nativos) e a visualização em Python (fácil manipulação de CSV e gráficos).
- JOCL/OpenCL é opcional: muitos usuários não têm drivers/núcleos compatíveis, por isso o projeto permite executar apenas-CPU sem falhas. Essa escolha facilita testes e compartilhamento do repositório.
- Medição simples: usei `System.currentTimeMillis()` por simplicidade e legibilidade; para medições mais precisas prefira `System.nanoTime()` (comentado no README).
- Kernel simples por clareza: o kernel atual testa cada posição independentemente — bom para prototipagem e comparações iniciais, porém não é o mais eficiente para muitos matches; refatorações futuras podem introduzir reduções por work-group para melhor desempenho.

Como navegar rapidamente

- Para rodar um exemplo rápido (Windows PowerShell): compile `src/`, execute `benchmark.ps1` e depois rode `python-frontend/plot_results.py results\\results.csv`.
- Para entender a implementação: abra `src/WordCountMain.java` (lógica serial e divisão por chunks) e `src/GPUWordCounter.java` (inicialização JOCL, preparação de buffers, invocação de kernel).

Se quiser, eu posso expandir ainda mais esta seção com um diagrama de dependências simples (ASCII) ou uma explicação linha-a-linha das funções públicas em `WordCountMain` e `GPUWordCounter`.

— Quick Start (checklist)

- [ ] Java 8+ instalado (`java -version`).
- [ ] (opcional GPU) `jocl-2.0.4.jar` em `wordcount/libs/`.
- [ ] (opcional GPU) libs nativas JOCL no `PATH` (Windows) ou `LD_LIBRARY_PATH`/`DYLD_LIBRARY_PATH` (Linux/macOS).
- [ ] (opcional plots) Python 3 + `pandas`, `matplotlib`.

— Compilar

Abra um terminal no diretório `wordcount/src` e rode (copy-paste):

Windows (PowerShell / cmd):
```powershell
cd path\to\wordcount\src
javac -cp ".;../libs/jocl-2.0.4.jar" WordCountMain.java GPUWordCounter.java
```

Se não tiver JOCL e não precisa do GPU, basta compilar `WordCountMain.java` isoladamente.

— Executar (exemplos copy-paste)

Formato:
```text
java -cp "<classpath>" WordCountMain <mode> <inputFile> <targetWord> <runs> <outputCsv>
```

Exemplo (Windows) — apenas serial (mais rápido de testar):
```powershell
java -cp ".;../libs/jocl-2.0.4.jar" WordCountMain serial ..\samples\MobyDick-217452.txt the 3 ..\results\results.csv
```

Exemplo (Windows) — todos os modos (requer JOCL native libs + drivers OpenCL):
```powershell
java -cp ".;../libs/jocl-2.0.4.jar" WordCountMain all ..\samples\MobyDick-217452.txt the 3 ..\results\results.csv
```

Exemplo (Unix/macOS):
```bash
java -cp ".:../libs/jocl-2.0.4.jar" WordCountMain all ../samples/MobyDick-217452.txt the 3 ../results/results.csv
```

— Gerar gráficos (opcional)

Instale dependências Python e execute o script de plot:

```powershell
pip install pandas matplotlib
python python-frontend/plot_results.py results\results.csv
```

— Troubleshooting rápido (GPU / JOCL)

- Erro: `UnsatisfiedLinkError` ao inicializar JOCL
	- Significa que as libs nativas (DLL/.so/.dylib) não estão no `PATH` ou `java.library.path`.
	- Solução rápida (Windows PowerShell):
		```powershell
		$env:PATH += ";C:\caminho\para\jocl\native"
		java -Djava.library.path="C:\caminho\para\jocl\native" -cp ".;../libs/jocl-2.0.4.jar" WordCountMain gpu ..\samples\MobyDick-217452.txt the 1 ..\results\r.csv
		```
- Resultado GPU divergente / zero
	- Teste com `cpu` OpenCL device (algumas plataformas expõem CPU OpenCL drivers).
	- O kernel usa `atomic_inc` — alguns dispositivos/implementações podem agir diferente; considere reescrever para redução por work-group se necessário.
- Procure por logs/stacktrace: `GPU counting failed:` (o `WordCountMain` imprime stack trace quando a chamada JOCL falha).

— Notas sobre comportamento e precisão

- `countSerial` permite matches sobrepostos (`idx += 1`). Se quiser evitar overlap, mude para `idx += target.length()`.
- `WordCountMain` usa `System.currentTimeMillis()` para medir; para medições mais precisas use `System.nanoTime()`.
- O kernel GPU compara bytes (UTF-8). Se seu `target` contiver caracteres multi-byte, garanta que a string passada ao programa esteja codificada da mesma forma.

— Resultado esperado

- `results/results.csv` com colunas: `method,file,target,count,millis,run`.
- `results_time.png` e `results_counts.png` gerados pelo script Python (quando executado).