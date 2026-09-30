@echo off
setlocal EnableDelayedExpansion
REM ============================================================================
REM  SRM Credit Engine - start/stop de toda a solucao. Portavel: NENHUM caminho
REM  de maquina especifica; tudo relativo a este .bat (%~dp0) e auto-detectado.
REM
REM    srm.bat start    sobe a solucao inteira e abre o painel no navegador
REM    srm.bat stop     derruba tudo (Docker e/ou modo local)
REM    srm.bat reset    (Docker) derruba e apaga o volume do banco -> volta ao seed
REM    srm.bat status   mostra o que esta no ar
REM    srm.bat logs     (Docker) acompanha os logs da API
REM
REM  Escolhe sozinho o melhor caminho disponivel:
REM    1) Docker rodando        -> docker compose (banco+API+web; nada mais preciso)
REM    2) senao, JDK 21+ e Node -> modo local (PostgreSQL 17 EMBARCADO, sem Docker)
REM  Mensagens em ASCII de proposito: o console do Windows corrompe acentos.
REM ============================================================================

set "ROOT=%~dp0"
set "API_PORT=8080"
set "WEB_PORT_DOCKER=80"
set "WEB_PORT_LOCAL=5173"
set "CMD=%~1"
if "%CMD%"=="" set "CMD=start"
cd /d "%ROOT%"

if /i "%CMD%"=="start"  goto :do_start
if /i "%CMD%"=="stop"   goto :do_stop
if /i "%CMD%"=="reset"  goto :do_reset
if /i "%CMD%"=="status" goto :do_status
if /i "%CMD%"=="logs"   goto :do_logs
echo Uso: srm.bat [start ^| stop ^| reset ^| status ^| logs]
exit /b 1

REM ============================================================================
:do_start
call :docker_up
if not errorlevel 1 (
  goto :start_docker
) else (
  goto :start_local
)

REM ----------------------------------------------------------------------------
:start_docker
echo [SRM] Docker detectado. Subindo banco + API + web via compose.
echo       ^(o 1o build baixa dependencias nos containers; pode demorar^)...
docker compose up -d --build
if errorlevel 1 (
  echo [ERRO] docker compose up falhou - veja as mensagens acima.
  exit /b 1
)
set "WEB_PORT=%WEB_PORT_DOCKER%"
call :wait_health %API_PORT% http://localhost:%API_PORT% API 90 "srm.bat logs"
if errorlevel 1 exit /b 1
call :wait_url http://localhost:%WEB_PORT%/ web 30 "docker compose logs web"
if errorlevel 1 exit /b 1
call :ready http://localhost
exit /b 0

REM ----------------------------------------------------------------------------
:start_local
echo [SRM] Docker nao esta rodando. Usando o modo local ^(PostgreSQL embarcado^).
call :resolve_java
if errorlevel 1 (
  echo [ERRO] Preciso de Docker Desktop rodando OU de um JDK 21+ instalado.
  echo        JDK procurado em: PATH, JAVA_HOME, "C:\Program Files\Java\jdk-*",
  echo        "C:\Program Files\Eclipse Adoptium\jdk-*", "%%USERPROFILE%%\.jdks\jdk-*"
  exit /b 1
)
echo [SRM] Java: "%JAVA_HOME%"
where npm >NUL 2>&1
if errorlevel 1 (
  echo [ERRO] Node/npm nao encontrado no PATH. Instale o Node 20+ ^(ou use o Docker^).
  exit /b 1
)
call :resolve_maven
echo [SRM] Maven: %MVN_DESC%

set "WEB_PORT=%WEB_PORT_LOCAL%"
call :port_pid %API_PORT%
if defined PORT_PID (
  echo [SRM] API ja esta no ar ^(porta %API_PORT%, PID !PORT_PID!^) - mantendo.
) else (
  echo [SRM] Subindo a API ^(porta %API_PORT%; o 1o boot compila^)...
  start "SRM API - PostgreSQL embarcado" /d "%ROOT%backend" cmd /k "set "JAVA_HOME=%JAVA_HOME%" && %MVN_CMD% spring-boot:test-run"
)
call :wait_health %API_PORT% http://localhost:%API_PORT% API 150 "a janela 'SRM API'"
if errorlevel 1 exit /b 1

call :port_pid %WEB_PORT%
if defined PORT_PID (
  echo [SRM] Frontend ja esta no ar ^(porta %WEB_PORT%, PID !PORT_PID!^) - mantendo.
) else (
  echo [SRM] Subindo o frontend ^(porta %WEB_PORT%^)...
  start "SRM Web - Vite" /d "%ROOT%frontend" cmd /k "if not exist node_modules npm install --no-audit --no-fund && npm run dev"
)
call :wait_url http://localhost:%WEB_PORT%/ web 60 "a janela 'SRM Web'"
if errorlevel 1 exit /b 1
call :ready http://localhost:%WEB_PORT%
exit /b 0

REM ============================================================================
:do_stop
set "STOPPED="
call :docker_up
if not errorlevel 1 (
  for /f %%N in ('docker compose ps -q 2^>NUL') do set "STOPPED=1"
  if defined STOPPED (
    echo [SRM] Derrubando os servicos Docker ^(dados do banco preservados^)...
    docker compose down
  )
)
call :kill_port %WEB_PORT_LOCAL% "frontend local"
call :kill_port %API_PORT% "API local"
if not defined STOPPED if not defined KILLED echo [SRM] Nada rodando.
exit /b 0

REM ============================================================================
:do_reset
call :docker_up
if errorlevel 1 (
  echo [ERRO] reset e so para o modo Docker. Com Docker parado nao ha volume a apagar.
  exit /b 1
)
echo [SRM] Derrubando e APAGANDO o volume do banco...
docker compose down -v
if errorlevel 1 exit /b 1
echo [SRM] Pronto: o proximo start recria o banco com o seed da demo.
exit /b 0

REM ============================================================================
:do_status
call :docker_up
if not errorlevel 1 docker compose ps
call :port_pid %API_PORT%
if defined PORT_PID (echo [SRM] API      : NO AR  ^(porta %API_PORT%, PID !PORT_PID!^)) else (echo [SRM] API      : parada ^(porta %API_PORT%^))
call :port_pid %WEB_PORT_DOCKER%
if defined PORT_PID (echo [SRM] Web 80   : NO AR  ^(Docker^)) else (echo [SRM] Web 80   : parado ^(Docker^))
call :port_pid %WEB_PORT_LOCAL%
if defined PORT_PID (echo [SRM] Web 5173 : NO AR  ^(local^)) else (echo [SRM] Web 5173 : parado ^(local^))
exit /b 0

REM ============================================================================
:do_logs
call :docker_up
if errorlevel 1 (
  echo [SRM] logs e so para o modo Docker. No modo local, veja a janela "SRM API".
  exit /b 1
)
docker compose logs -f api
exit /b 0

REM ===========================================================================
REM  Sub-rotinas
REM ===========================================================================

REM  errorlevel 0 = Docker instalado E rodando; 1 = indisponivel.
:docker_up
where docker >NUL 2>&1 || exit /b 1
docker info >NUL 2>&1 || exit /b 1
exit /b 0

REM  Espera a porta %1 ficar saudavel via /actuator/health em %2. %3=rotulo
REM  %4=tentativas (x2s) %5=onde diagnosticar. errorlevel 1 se estourar.
:wait_health
echo [SRM] Aguardando %~3 ficar saudavel...
set "HC_OK="
for /l %%i in (1,1,%~4) do (
  if not defined HC_OK (
    curl -fs -o NUL %~2/actuator/health 2>NUL && set "HC_OK=1"
    if not defined HC_OK call :sleep2
  )
)
if not defined HC_OK (
  echo [ERRO] %~3 nao respondeu em %~2/actuator/health. Diagnostico: %~5
  exit /b 1
)
echo [SRM] %~3 saudavel.
exit /b 0

REM  Espera a URL %1 responder. %2=rotulo %3=tentativas(x2s) %4=diagnostico.
:wait_url
set "WU_OK="
for /l %%i in (1,1,%~3) do (
  if not defined WU_OK (
    curl -fs -o NUL %~1 2>NUL && set "WU_OK=1"
    if not defined WU_OK call :sleep2
  )
)
if not defined WU_OK (
  echo [ERRO] O %~2 nao respondeu em %~1. Diagnostico: %~4
  exit /b 1
)
exit /b 0

REM  Banner final + abre o navegador em %1.
:ready
echo.
echo [SRM] Tudo no ar:
echo        Painel da mesa:  %~1
echo        API:             http://localhost:%API_PORT%
echo        Swagger:         http://localhost:%API_PORT%/swagger-ui.html
echo        Metricas:        http://localhost:%API_PORT%/actuator/prometheus
echo [SRM] Para derrubar: srm.bat stop
start "" %~1
exit /b 0

REM  PID que escuta a porta %1 (vazio se nenhum). Match EXATO de porta.
:port_pid
set "PORT_PID="
for /f %%P in ('powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort %1 -State Listen -ErrorAction SilentlyContinue ^| Select-Object -First 1).OwningProcess" 2^>NUL') do set "PORT_PID=%%P"
exit /b 0

REM  Mata a ARVORE do processo da porta %1 (o PG embarcado e filho do java).
:kill_port
call :port_pid %1
if defined PORT_PID (
  echo [SRM] Derrubando %~2 ^(porta %1, PID !PORT_PID! + filhos^)...
  taskkill /T /F /PID !PORT_PID! >NUL 2>&1
  set "KILLED=1"
)
exit /b 0

REM  Acha o mvn: 'mvn' no PATH -> senao o wrapper do projeto (portavel, no repo).
:resolve_maven
where mvn >NUL 2>&1
if not errorlevel 1 (
  set "MVN_CMD=mvn"
  set "MVN_DESC=mvn do PATH"
  exit /b 0
)
set "MVN_CMD=mvnw.cmd"
set "MVN_DESC=wrapper do projeto (mvnw.cmd)"
exit /b 0

REM  Acha o JDK em locais padrao de qualquer Windows.
:resolve_java
where java >NUL 2>&1
if not errorlevel 1 (
  if not defined JAVA_HOME for /f "delims=" %%J in ('where java') do if not defined JAVA_HOME set "JAVA_HOME=%%~dpJ.."
  exit /b 0
)
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" exit /b 0
call :find_jdk "C:\Program Files\Java\jdk-*"                 && exit /b 0
call :find_jdk "C:\Program Files\Eclipse Adoptium\jdk-*"      && exit /b 0
call :find_jdk "C:\Program Files\Microsoft\jdk-*"             && exit /b 0
call :find_jdk "%USERPROFILE%\.jdks\jdk-*"                    && exit /b 0
exit /b 1

REM  Define JAVA_HOME com o 1o diretorio que casar com %1 e tiver java.exe.
:find_jdk
for /d %%J in (%~1) do (
  if exist "%%~fJ\bin\java.exe" (
    set "JAVA_HOME=%%~fJ"
    exit /b 0
  )
)
exit /b 1

REM  ~2s a prova de ambiente: timeout.exe recusa stdin redirecionado e um Git
REM  Bash no PATH poe o timeout do GNU na frente - ping nao tem nenhum dos dois.
:sleep2
ping -n 3 127.0.0.1 >NUL
exit /b 0
