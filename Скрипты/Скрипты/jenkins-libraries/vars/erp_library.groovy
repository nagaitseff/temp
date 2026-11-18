/**
 * Сборка основной конфигурации из XML через автономный сервер
 * и публикация файла 1Cv8.cf в каталог артефактов ветки.
 *
 * args.branchName - обязательное имя ветки для построения пути артефактов.
 * args.debugLog   - необязательный флаг подробного логирования и сохранения временного каталога сборки.
 *
 * Возвращает полный UNC-путь к опубликованному CF-файлу.
 */
def buildCfFromXmlAndPublish(Map args = [:]) {
    validateRequiredArg(args, 'branchName')

    String branchName = normalizeValue(args.branchName)
    boolean debugLog = toBooleanArg(args.debugLog)

    String workspace = env.WORKSPACE
    String artifactsRoot = '\\\\ukm-sapp108\\artifacts'
    String importDir = "${workspace}\\src\\cf"
    String targetRoot = "${workspace}\\autonomous_ib"
    String dbPath = "${targetRoot}\\db-data"
    String dataPath = "${targetRoot}\\data"
    String localCfPath = "${workspace}\\1Cv8.cf"
    String remoteDir = buildArtifactsPath(artifactsRoot, branchName)
    String remoteCfPath = "${remoteDir}\\1Cv8.cf"
    String debugLogPath = "${targetRoot}\\build_cf.log"

    try {
        echo '[INFO] Запуск сборки CF из XML и публикации артефакта'
        echo "[INFO] DEBUG_LOG: ${debugLog}"
        echo "[INFO] WORKSPACE: ${workspace}"
        echo "[INFO] Каталог исходников XML: ${importDir}"
        echo "[INFO] Ветка: ${branchName}"
        echo "[INFO] Каталог артефактов: ${remoteDir}"

        if (!fileExists(importDir)) {
            error("[ERROR] Каталог исходников XML не найден: ${importDir}")
        }

        String ibcmdPath = resolveLatestIbcmdPath()

        if (!debugLog) {
            prepareCleanDirectory(targetRoot)
        } else {
            echo "[DEBUG] Очистка ${targetRoot} перед сборкой CF пропущена"
            prepareDirectory(targetRoot)
        }

        prepareDirectory(dbPath)
        prepareDirectory(dataPath)
        prepareDirectory(remoteDir)

        createEmptyInfobase(ibcmdPath, dataPath, dbPath, debugLog, debugLogPath)
        importConfigFromXml(ibcmdPath, dataPath, dbPath, importDir, debugLog, debugLogPath)
        saveCfFile(ibcmdPath, dataPath, dbPath, localCfPath, debugLog, debugLogPath)
        copyCfToArtifacts(localCfPath, remoteCfPath, debugLog, debugLogPath)

        if (debugLog && fileExistsByBat(debugLogPath)) {
            printDebugLog(debugLogPath, 'build_cf.log')
        }

        echo "[INFO] Файл конфигурации опубликован: ${remoteCfPath}"
        return remoteCfPath
    } finally {
        cleanupPath(dbPath, true)
        cleanupPath(dataPath, true)

        if (!debugLog) {
            cleanupPath(targetRoot, true)
            cleanupPath(localCfPath, false)
        } else {
            echo "[DEBUG] Каталог сборки CF сохранён для ручной диагностики: ${targetRoot}"
            echo "[DEBUG] Лог сборки CF: ${debugLogPath}"
        }
    }
}

/**
 * Сборка расширения Mechel из XML через автономный сервер
 * и публикация файла Mechel.cfe в каталог артефактов ветки.
 *
 * args.branchName - обязательное имя ветки для построения пути артефактов.
 * args.debugLog   - необязательный флаг подробного логирования и сохранения временного каталога сборки.
 *
 * Возвращает полный UNC-путь к опубликованному CFE-файлу.
 */
def buildCfeFromXmlAndPublish(Map args = [:]) {
    validateRequiredArg(args, 'branchName')

    String branchName = normalizeValue(args.branchName)
    boolean debugLog = toBooleanArg(args.debugLog)

    String workspace = env.WORKSPACE
    String artifactsRoot = '\\\\ukm-sapp108\\artifacts'
    String extensionName = 'Mechel'
    String extensionPrefix = 'Mechel'
    String importDir = "${workspace}\\src\\cfe\\Mechel"
    String targetRoot = "${workspace}\\autonomous_ext_ib"
    String dbPath = "${targetRoot}\\db-data"
    String dataPath = "${targetRoot}\\data"
    String localCfePath = "${workspace}\\Mechel.cfe"
    String remoteDir = buildArtifactsPath(artifactsRoot, branchName)
    String remoteCfePath = "${remoteDir}\\Mechel.cfe"
    String debugLogPath = "${targetRoot}\\build_cfe.log"

    try {
        echo '[INFO] Запуск сборки CFE из XML и публикации артефакта'
        echo "[INFO] DEBUG_LOG: ${debugLog}"
        echo "[INFO] WORKSPACE: ${workspace}"
        echo "[INFO] Каталог исходников XML расширения: ${importDir}"
        echo "[INFO] Имя расширения: ${extensionName}"
        echo "[INFO] Ветка: ${branchName}"
        echo "[INFO] Каталог артефактов: ${remoteDir}"

        if (!fileExists(importDir)) {
            error("[ERROR] Каталог исходников XML расширения не найден: ${importDir}")
        }

        String ibcmdPath = resolveLatestIbcmdPath()
        String designerPath = resolveLatestDesignerPath()

        if (!debugLog) {
            prepareCleanDirectory(targetRoot)
        } else {
            echo "[DEBUG] Очистка ${targetRoot} перед сборкой CFE пропущена"
            prepareDirectory(targetRoot)
        }

        prepareDirectory(dbPath)
        prepareDirectory(dataPath)
        prepareDirectory(remoteDir)

        createEmptyInfobase(ibcmdPath, dataPath, dbPath, debugLog, debugLogPath)
        createExtension(ibcmdPath, dataPath, dbPath, extensionName, extensionPrefix, debugLog, debugLogPath)
        waitForStandaloneRelease(10, debugLog, debugLogPath, 'После создания расширения')

        importExtensionFromXml(ibcmdPath, dataPath, dbPath, extensionName, importDir, debugLog, debugLogPath)
        waitForStandaloneRelease(15, debugLog, debugLogPath, 'После импорта XML расширения')

        dumpExtensionToCfe(designerPath, dbPath, localCfePath, extensionName, debugLog, debugLogPath)
        copyCfeToArtifacts(localCfePath, remoteCfePath, debugLog, debugLogPath)

        if (debugLog && fileExistsByBat(debugLogPath)) {
            printDebugLog(debugLogPath, 'build_cfe.log')
        }

        echo "[INFO] Файл расширения опубликован: ${remoteCfePath}"
        return remoteCfePath
    } finally {
        cleanupPath(dbPath, true)
        cleanupPath(dataPath, true)

        if (!debugLog) {
            cleanupPath(targetRoot, true)
            cleanupPath(localCfePath, false)
        } else {
            echo "[DEBUG] Каталог сборки CFE сохранён для ручной диагностики: ${targetRoot}"
            echo "[DEBUG] Лог сборки CFE: ${debugLogPath}"
        }
    }
}

/**
 * Поиск пути к самой новой установленной версии ibcmd.exe.
 *
 * Возвращает полный путь к ibcmd.exe.
 */
def resolveLatestIbcmdPath() {
    String root = 'C:\\Program Files\\1cv8'
    ensurePlatformRootExists(root)

    String output = listPlatformDirectories(root)
    echo "[DEBUG] Каталоги в ${root}:\n${output}"

    List<String> candidates = extractVersionDirectories(output, root)
    List<Map> versions = candidates.collect { name ->
        String ibcmd = "${root}\\${name}\\bin\\ibcmd.exe"
        return fileExistsByBat(ibcmd) ? [name: name, ibcmd: ibcmd] : null
    }.findAll { it != null }

    if (!versions) {
        error("[ERROR] Не найден ibcmd.exe в C:\\Program Files\\1cv8\\<version>\\bin")
    }

    Map latest = selectLatestVersion(versions)
    echo "[INFO] Найден ibcmd.exe: ${latest.ibcmd}"
    return latest.ibcmd
}

/**
 * Поиск пути к самой новой установленной версии 1cv8.exe
 * для запуска конфигуратора.
 *
 * Возвращает полный путь к 1cv8.exe.
 */
def resolveLatestDesignerPath() {
    String root = 'C:\\Program Files\\1cv8'
    ensurePlatformRootExists(root)

    String output = listPlatformDirectories(root)
    echo "[DEBUG] Каталоги в ${root} для поиска DESIGNER:\n${output}"

    List<String> candidates = extractVersionDirectories(output, root)
    List<Map> versions = candidates.collect { name ->
        String designer = "${root}\\${name}\\bin\\1cv8.exe"
        return fileExistsByBat(designer) ? [name: name, designer: designer] : null
    }.findAll { it != null }

    if (!versions) {
        error("[ERROR] Не найден 1cv8.exe в C:\\Program Files\\1cv8\\<version>\\bin")
    }

    Map latest = selectLatestVersion(versions)
    echo "[INFO] Найден DESIGNER: ${latest.designer}"
    return latest.designer
}

/**
 * Создание пустой файловой информационной базы через ibcmd.
 */
def createEmptyInfobase(String ibcmdPath, String dataPath, String dbPath, boolean debugLog = false, String debugLogPath = null) {
    echo '[INFO] Создание пустой файловой информационной базы'

    String scriptBody = """
@echo off
chcp 65001 >nul
"${ibcmdPath}" infobase create --data="${dataPath}" --db-path="${dbPath}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'createEmptyInfobase',
        [
            IBCMD_PATH: ibcmdPath,
            DATA_PATH : dataPath,
            DB_PATH   : dbPath
        ],
        [dataPath, dbPath]
    )

    if (result != 0) {
        error("[ERROR] Ошибка создания пустой файловой базы. Код завершения: ${result}")
    }
}

/**
 * Импорт основной конфигурации из XML в файловую базу через ibcmd.
 */
def importConfigFromXml(String ibcmdPath, String dataPath, String dbPath, String importDir, boolean debugLog = false, String debugLogPath = null) {
    echo '[INFO] Загрузка конфигурации из XML'

    String scriptBody = """
@echo off
chcp 65001 >nul
"${ibcmdPath}" infobase config import --data="${dataPath}" --db-path="${dbPath}" "${importDir}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'importConfigFromXml',
        [
            IBCMD_PATH : ibcmdPath,
            DATA_PATH  : dataPath,
            DB_PATH    : dbPath,
            IMPORT_DIR : importDir
        ],
        [dataPath, dbPath, importDir]
    )

    if (result != 0) {
        error("[ERROR] Ошибка загрузки конфигурации из XML. Код завершения: ${result}")
    }
}

/**
 * Сохранение основной конфигурации из файловой базы в локальный файл CF через ibcmd.
 */
def saveCfFile(String ibcmdPath, String dataPath, String dbPath, String localCfPath, boolean debugLog = false, String debugLogPath = null) {
    if (fileExists(localCfPath)) {
        cleanupPath(localCfPath, false)
    }

    echo '[INFO] Сохранение конфигурации в файл CF'

    String scriptBody = """
@echo off
chcp 65001 >nul
"${ibcmdPath}" infobase config save --data="${dataPath}" --db-path="${dbPath}" "${localCfPath}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'saveCfFile',
        [
            IBCMD_PATH    : ibcmdPath,
            DATA_PATH     : dataPath,
            DB_PATH       : dbPath,
            LOCAL_CF_PATH : localCfPath
        ],
        [dataPath, dbPath, localCfPath]
    )

    if (result != 0) {
        error("[ERROR] Ошибка сохранения файла CF. Код завершения: ${result}")
    }

    if (!fileExists(localCfPath)) {
        error("[ERROR] Сборка завершилась без ошибки, но файл CF не найден: ${localCfPath}")
    }
}

/**
 * Копирование локального CF-файла в каталог артефактов.
 */
def copyCfToArtifacts(String localCfPath, String remoteCfPath, boolean debugLog = false, String debugLogPath = null) {
    echo "[INFO] Копирование CF в каталог артефактов: ${remoteCfPath}"

    String scriptBody = """
@echo off
chcp 65001 >nul
copy /Y "${localCfPath}" "${remoteCfPath}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'copyCfToArtifacts',
        [
            LOCAL_CF_PATH  : localCfPath,
            REMOTE_CF_PATH : remoteCfPath
        ],
        [localCfPath]
    )

    if (result != 0) {
        error("[ERROR] Ошибка копирования CF в каталог артефактов. Код завершения: ${result}")
    }
}

/**
 * Создание расширения в файловой базе через ibcmd.
 */
def createExtension(
    String ibcmdPath,
    String dataPath,
    String dbPath,
    String extensionName,
    String extensionPrefix,
    boolean debugLog = false,
    String debugLogPath = null
) {
    echo "[INFO] Создание расширения ${extensionName}"

    String scriptBody = """
@echo off
chcp 65001 >nul
"${ibcmdPath}" infobase config extension create --data="${dataPath}" --db-path="${dbPath}" --name="${extensionName}" --name-prefix="${extensionPrefix}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'createExtension',
        [
            IBCMD_PATH       : ibcmdPath,
            DATA_PATH        : dataPath,
            DB_PATH          : dbPath,
            EXTENSION_NAME   : extensionName,
            EXTENSION_PREFIX : extensionPrefix
        ],
        [dataPath, dbPath]
    )

    if (result != 0) {
        error("[ERROR] Ошибка создания расширения ${extensionName}. Код завершения: ${result}")
    }
}

/**
 * Импорт XML-выгрузки расширения в файловую базу через ibcmd.
 */
def importExtensionFromXml(
    String ibcmdPath,
    String dataPath,
    String dbPath,
    String extensionName,
    String importDir,
    boolean debugLog = false,
    String debugLogPath = null
) {
    echo "[INFO] Загрузка XML расширения ${extensionName}"

    String scriptBody = """
@echo off
chcp 65001 >nul
"${ibcmdPath}" infobase config import --data="${dataPath}" --db-path="${dbPath}" --extension="${extensionName}" "${importDir}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'importExtensionFromXml',
        [
            IBCMD_PATH     : ibcmdPath,
            DATA_PATH      : dataPath,
            DB_PATH        : dbPath,
            EXTENSION_NAME : extensionName,
            IMPORT_DIR     : importDir
        ],
        [dataPath, dbPath, importDir]
    )

    if (result != 0) {
        error("[ERROR] Ошибка загрузки XML расширения ${extensionName}. Код завершения: ${result}")
    }
}

/**
 * Выдержка паузы между тяжелыми операциями автономного сервера и конфигуратора.
 */
def waitForStandaloneRelease(int seconds, boolean debugLog = false, String debugLogPath = null, String reason = '') {
    echo "[INFO] Ожидание ${seconds} сек. для освобождения ресурсов файловой базы"
    if (debugLog && debugLogPath) {
        appendDebugLine(debugLogPath, "WAIT ${seconds} sec. ${reason}")
    }
    Thread.sleep(seconds * 1000L)
}

/**
 * Выгрузка расширения из файловой базы в локальный файл CFE через конфигуратор.
 */
def dumpExtensionToCfe(
    String designerPath,
    String dbPath,
    String localCfePath,
    String extensionName,
    boolean debugLog = false,
    String debugLogPath = null
) {
    if (fileExists(localCfePath)) {
        cleanupPath(localCfePath, false)
    }

    echo "[INFO] Выгрузка расширения ${extensionName} в файл CFE"

    String scriptBody = """
@echo off
chcp 65001 >nul
"${designerPath}" DESIGNER /DisableStartupMessages /DisableStartupDialogs /F "${dbPath}" /DumpCfg "${localCfePath}" -Extension "${extensionName}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'dumpExtensionToCfe',
        [
            DESIGNER_PATH  : designerPath,
            TARGET_ROOT    : dbPath,
            LOCAL_CFE_PATH : localCfePath,
            EXTENSION_NAME : extensionName
        ],
        [dbPath]
    )

    if (result != 0) {
        error("[ERROR] Ошибка выгрузки расширения ${extensionName} в файл CFE. Код завершения: ${result}")
    }

    if (!fileExists(localCfePath)) {
        error("[ERROR] Выгрузка завершилась без ошибки, но файл CFE не найден: ${localCfePath}")
    }
}

/**
 * Копирование локального CFE-файла в каталог артефактов.
 */
def copyCfeToArtifacts(String localCfePath, String remoteCfePath, boolean debugLog = false, String debugLogPath = null) {
    echo "[INFO] Копирование CFE в каталог артефактов: ${remoteCfePath}"

    String scriptBody = """
@echo off
chcp 65001 >nul
copy /Y "${localCfePath}" "${remoteCfePath}"
"""

    int result = runBatWithOptionalDebugLog(
        scriptBody,
        debugLog,
        debugLogPath,
        'copyCfeToArtifacts',
        [
            LOCAL_CFE_PATH  : localCfePath,
            REMOTE_CFE_PATH : remoteCfePath
        ],
        [localCfePath]
    )

    if (result != 0) {
        error("[ERROR] Ошибка копирования CFE в каталог артефактов. Код завершения: ${result}")
    }
}

/**
 * Проверка существования корневого каталога платформы 1С.
 */
def ensurePlatformRootExists(String root) {
    int rootStatus = bat(
        returnStatus: true,
        encoding: 'UTF-8',
        script: """
@echo off
if exist "${root}" (
    exit /b 0
) else (
    exit /b 1
)
"""
    )

    if (rootStatus != 0) {
        error("[ERROR] Не найден каталог платформы 1С: ${root}")
    }
}

/**
 * Получение списка подкаталогов в каталоге платформы 1С.
 */
def listPlatformDirectories(String root) {
    String output = bat(
        returnStdout: true,
        encoding: 'UTF-8',
        script: """
@echo off
cd /d "${root}"
for /d %%D in (*) do @echo %%D
"""
    ).trim()

    if (!output) {
        error("[ERROR] В каталоге ${root} не найдено ни одной установленной версии платформы 1С")
    }

    return output
}

/**
 * Выделение из списка директорий только тех имен,
 * которые выглядят как версия платформы 8.x.x.x.
 */
def extractVersionDirectories(String output, String root) {
    List<String> candidates = output
        .split('\\r?\\n')
        .collect { it.trim() }
        .collect { line ->
            def parts = line.split('\\.')
            if (parts.size() == 4 && parts.every { it.isInteger() }) {
                return line
            }
            return null
        }
        .findAll { it != null }

    if (!candidates) {
        error("[ERROR] В каталоге ${root} не найдено ни одной директории с версией вида 8.x.x.x")
    }

    return candidates
}

/**
 * Проверка существования файла или каталога через bat.
 */
def fileExistsByBat(String path) {
    int status = bat(
        returnStatus: true,
        encoding: 'UTF-8',
        script: """
@echo off
if exist "${path}" (
    exit /b 0
) else (
    exit /b 1
)
"""
    )

    return status == 0
}

/**
 * Создание каталога, если он отсутствует.
 */
def prepareDirectory(String path) {
    int result = bat(
        returnStatus: true,
        encoding: 'UTF-8',
        script: """
@echo off
chcp 65001 >nul
if not exist "${path}" mkdir "${path}"
"""
    )

    if (result != 0) {
        error("[ERROR] Не удалось создать каталог: ${path}. Код завершения: ${result}")
    }
}

/**
 * Очистка каталога и повторное создание.
 */
def prepareCleanDirectory(String path) {
    cleanupPath(path, true)
    prepareDirectory(path)
}

/**
 * Удаление файла или каталога с проверкой результата.
 *
 * Делает до трех попыток с паузой между ними.
 * Если путь остался после всех попыток — завершает шаг ошибкой.
 */
def cleanupPath(String path, boolean isDirectory) {

    if (path == null || path.trim().isEmpty()) {
        error('[ERROR] Не указан путь для удаления')
    }

    String deleteCommand = isDirectory
        ? "rd /s /q \"${path}\""
        : "del /f /q \"${path}\""

    for (int attempt = 1; attempt <= 3; attempt++) {

        int result = bat(
            returnStatus: true,
            encoding: 'UTF-8',
            script: """
@echo off
chcp 65001 >nul

if not exist "${path}" exit /b 0

${deleteCommand}

if exist "${path}" exit /b 1

exit /b 0
"""
        )

        if (result == 0) {
            echo "[INFO] Путь удалён или отсутствует: ${path}"
            return
        }

        echo "[WARN] Не удалось удалить ${path}. Попытка ${attempt}/3"

        if (attempt < 3) {
            sleep time: 3, unit: 'SECONDS'
        }
    }

    error("[ERROR] Путь остался после трех попыток удаления: ${path}")
}

/**
 * Построение UNC-пути к каталогу артефактов по имени ветки.
 */
def buildArtifactsPath(String artifactsRoot, String branchName) {
    return artifactsRoot + '\\' + branchName.replace('/', '\\')
}

/**
 * Проверка наличия обязательного аргумента в map-параметрах.
 */
def validateRequiredArg(Map args, String name) {
    def value = args[name]
    if (value == null || value.toString().trim().isEmpty()) {
        error("[ERROR] Не заполнен обязательный параметр: ${name}")
    }
}

/**
 * Нормализация строкового значения.
 */
def normalizeValue(def value) {
    return value.toString().trim()
}

/**
 * Преобразование аргумента в boolean.
 */
def toBooleanArg(def value) {
    if (value == null) {
        return false
    }
    return value.toString().trim().toBoolean()
}

/**
 * Запуск bat-сценария с дополнительным логированием при debugLog=true.
 */
def runBatWithOptionalDebugLog(
    String scriptBody,
    boolean debugLog = false,
    String debugLogPath = null,
    String operationName = 'operation',
    Map context = [:],
    List<String> dirsToList = []
) {
    String finalScript = scriptBody

    if (debugLog && debugLogPath) {
        String logFileVar = '%LOG_FILE%'
        StringBuilder sb = new StringBuilder()
        sb.append("@echo off\n")
        sb.append("set LOG_FILE=${quoteForCmd(debugLogPath)}\n")
        sb.append("if not exist \"${getParentPath(debugLogPath)}\" mkdir \"${getParentPath(debugLogPath)}\"\n")
        sb.append("echo.>> ${logFileVar}\n")
        sb.append("echo ==================================================>> ${logFileVar}\n")
        sb.append("echo OPERATION: ${escapeForCmdEcho(operationName)}>> ${logFileVar}\n")
        sb.append("echo DATE: %DATE% %TIME%>> ${logFileVar}\n")
        context.each { k, v ->
            sb.append("echo ${escapeForCmdEcho(k.toString())}: ${escapeForCmdEcho(v?.toString() ?: '')}>> ${logFileVar}\n")
        }
        if (dirsToList != null && !dirsToList.isEmpty()) {
            sb.append("echo ----- DIRECTORY SNAPSHOT BEFORE ----- >> ${logFileVar}\n")
            dirsToList.each { dirPath ->
                sb.append("echo [DIR] ${escapeForCmdEcho(dirPath)} >> ${logFileVar}\n")
                sb.append("if exist \"${dirPath}\" (\n")
                sb.append("  dir \"${dirPath}\" /a >> ${logFileVar} 2>&1\n")
                sb.append(") else (\n")
                sb.append("  echo [MISSING] ${escapeForCmdEcho(dirPath)} >> ${logFileVar}\n")
                sb.append(")\n")
            }
        }
        sb.append("echo ----- COMMAND OUTPUT BEGIN ----- >> ${logFileVar}\n")
        sb.append(scriptBody.trim()).append(" >> ${logFileVar} 2>&1\n")
        sb.append("set RC=%ERRORLEVEL%\n")
        sb.append("echo ----- COMMAND OUTPUT END ----- >> ${logFileVar}\n")
        if (dirsToList != null && !dirsToList.isEmpty()) {
            sb.append("echo ----- DIRECTORY SNAPSHOT AFTER ----- >> ${logFileVar}\n")
            dirsToList.each { dirPath ->
                sb.append("echo [DIR] ${escapeForCmdEcho(dirPath)} >> ${logFileVar}\n")
                sb.append("if exist \"${dirPath}\" (\n")
                sb.append("  dir \"${dirPath}\" /a >> ${logFileVar} 2>&1\n")
                sb.append(") else (\n")
                sb.append("  echo [MISSING] ${escapeForCmdEcho(dirPath)} >> ${logFileVar}\n")
                sb.append(")\n")
            }
        }
        sb.append("echo EXIT_CODE: %RC% >> ${logFileVar}\n")
        sb.append("exit /b %RC%\n")
        finalScript = sb.toString()
    }

    return bat(
        returnStatus: true,
        encoding: 'UTF-8',
        script: finalScript
    )
}

/**
 * Добавление строки в debug log.
 */
def appendDebugLine(String debugLogPath, String text) {
    bat(
        returnStatus: true,
        encoding: 'UTF-8',
        script: """
@echo off
if not exist "${getParentPath(debugLogPath)}" mkdir "${getParentPath(debugLogPath)}"
echo ${escapeForCmdEcho(text)}>> "${debugLogPath}"
"""
    )
}

/**
 * Печать debug log в консоль Jenkins.
 */
def printDebugLog(String debugLogPath, String title = 'debug.log') {
    echo "[DEBUG] Содержимое ${title}:"
    bat(
        returnStatus: true,
        encoding: 'UTF-8',
        script: """
@echo off
if exist "${debugLogPath}" (
    type "${debugLogPath}"
) else (
    echo [DEBUG] Файл лога не найден: "${debugLogPath}"
)
"""
    )
}

/**
 * Возвращает родительский каталог пути.
 */
@NonCPS
def getParentPath(String path) {
    int idx = path.lastIndexOf('\\')
    return idx > 0 ? path.substring(0, idx) : path
}

/**
 * Кавычки для cmd-пути.
 */
@NonCPS
def quoteForCmd(String value) {
    return "\"${value}\""
}

/**
 * Экранирование текста для echo в cmd.
 */
@NonCPS
def escapeForCmdEcho(String value) {
    if (value == null) {
        return ''
    }

    return value
        .replace('^', '^^')
        .replace('&', '^&')
        .replace('|', '^|')
        .replace('<', '^<')
        .replace('>', '^>')
}

/**
 * Выбор самой новой версии платформы из списка кандидатов.
 */
@NonCPS
def selectLatestVersion(List<Map> versions) {
    if (versions == null || versions.isEmpty()) {
        return null
    }

    Map latest = versions[0]

    for (int i = 1; i < versions.size(); i++) {
        if (compareVersions(versions[i].name, latest.name) > 0) {
            latest = versions[i]
        }
    }

    return latest
}

/**
 * Сравнение двух строковых версий вида 8.3.27.1688 по сегментам.
 */
@NonCPS
def compareVersions(String left, String right) {
    List<Integer> lv = left.split('\\.').collect { it as int }
    List<Integer> rv = right.split('\\.').collect { it as int }

    int maxSize = Math.max(lv.size(), rv.size())

    for (int i = 0; i < maxSize; i++) {
        int l = i < lv.size() ? lv[i] : 0
        int r = i < rv.size() ? rv[i] : 0

        if (l > r) {
            return 1
        }
        if (l < r) {
            return -1
        }
    }

    return 0
}