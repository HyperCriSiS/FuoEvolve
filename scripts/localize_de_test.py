from pathlib import Path
import re

T = {
    '返回':'Zurück','取消':'Abbrechen','确定':'OK','删除':'Löschen','关闭':'Schließen','保存':'Speichern','搜索':'Suchen','重试':'Erneut versuchen','未命名':'Unbenannt','未知歌曲':'Unbekannter Titel','未知歌手':'Unbekannter Interpret','未知专辑':'Unbekanntes Album','播放':'Abspielen','歌曲':'Titel','歌手':'Interpreten','专辑':'Alben','歌单':'Playlists','本地':'Lokal','用户':'Benutzer','收藏':'Favoriten','推荐':'Empfohlen','探索':'Entdecken','我的':'Meine Musik','下载':'Downloads','设置':'Einstellungen','已收藏':'Favorisiert','暂无内容':'Keine Inhalte','加载更多':'Mehr laden','分享':'Teilen','关注':'Folgen','已关注':'Gefolgt','新建':'Neu','导入':'Importieren','完成':'Fertig','失败':'Fehlgeschlagen','状态':'Status','错误':'Fehler','网页':'Web',
    '听歌识曲':'Musik erkennen','为你推荐':'Für dich empfohlen','我的常听':'Oft gehört','本地歌单':'Lokale Playlists','暂无本地歌单，可新建或导入 .fuo 文件':'Noch keine lokalen Playlists. Erstelle eine neue oder importiere eine .fuo-Datei.','本地 · ${p.tracks.size} 首':'Lokal · ${p.tracks.size} Titel','我的歌曲':'Meine Titel','新建本地歌单':'Lokale Playlist erstellen','在 ${provider?.providerName.orEmpty()} 新建歌单':'Playlist bei ${provider?.providerName.orEmpty()} erstellen','导入本地歌单':'Lokale Playlist importieren','《${preview.title}》 · ${preview.tracks.size} 首':'„${preview.title}“ · ${preview.tracks.size} Titel','新建导入':'Als neue importieren','替换同名':'Gleichnamige ersetzen','歌单名称':'Playlistname','创建':'Erstellen',
    '歌曲、歌手或专辑':'Titel, Interpret oder Album','全部':'Alle','删除搜索历史':'Suchverlauf löschen','确定删除“$keyword”吗？':'„$keyword“ wirklich löschen?','搜索历史':'Suchverlauf','没有歌手结果':'Keine Interpreten gefunden','没有专辑结果':'Keine Alben gefunden','没有歌单结果':'Keine Playlists gefunden','没有视频结果':'Keine Videos gefunden','歌曲 ${uiState.searchResults.size}':'Titel ${uiState.searchResults.size}','歌手 ${uiState.providerSearchResults.artists.size}':'Interpreten ${uiState.providerSearchResults.artists.size}','专辑 ${uiState.providerSearchResults.albums.size}':'Alben ${uiState.providerSearchResults.albums.size}','歌单 ${uiState.providerSearchResults.playlists.size}':'Playlists ${uiState.providerSearchResults.playlists.size}','视频 ${uiState.providerSearchResults.videos.size}':'Videos ${uiState.providerSearchResults.videos.size}','输入关键词查找音乐':'Suchbegriff eingeben','没有结果':'Keine Ergebnisse',
    '下载管理':'Downloadverwaltung','下载任务':'Downloadaufträge','已完成':'Abgeschlossen','暂无已完成下载':'Keine abgeschlossenen Downloads','展开更多':'Mehr anzeigen','删除下载':'Download löschen','该下载任务':'Diesen Downloadauftrag','同时删除本地文件':'Lokale Datei ebenfalls löschen','删除后将清理保留的临时文件，无法继续下载。':'Dabei werden auch temporäre Dateien gelöscht; der Download kann danach nicht fortgesetzt werden.','暂停下载':'Download pausieren','继续下载':'Download fortsetzen','重试下载':'Download erneut versuchen','等待下载':'Wartet auf Download','下载中':'Wird heruntergeladen','已暂停，可继续':'Pausiert, kann fortgesetzt werden','下载失败':'Download fehlgeschlagen',
    '删除歌单':'Playlist löschen','导出歌单':'Playlist exportieren','分享歌单文件':'Playlist-Datei teilen','未命名歌单':'Unbenannte Playlist','本地文件 · ${uiState.selectedTracks.size} 首':'Lokale Dateien · ${uiState.selectedTracks.size} Titel','歌单暂无歌曲':'Die Playlist enthält noch keine Titel','删除本地歌单？':'Lokale Playlist löschen?','将删除《${displayPlaylist.title}》，此操作无法撤销。':'„${displayPlaylist.title}“ wird gelöscht. Dies kann nicht rückgängig gemacht werden.',
    '本地 · ${collection.trackCount} 首':'Lokal · ${collection.trackCount} Titel','暂无歌曲':'Keine Titel','本地文件夹 · $trackCount 首':'Lokaler Ordner · $trackCount Titel','本地 · 歌手 · $trackCount 首':'Lokal · Interpret · $trackCount Titel','本地 · 专辑 · $trackCount 首':'Lokal · Album · $trackCount Titel','允许读取图片以显示封面':'Zugriff auf Bilder erlauben, um Cover anzuzeigen','授权图片':'Bilderzugriff erlauben','修改元信息':'Metadaten bearbeiten','标题':'Titel','搜索补充':'Ergänzungen suchen','没有可用音源':'Keine verfügbaren Musikquellen','使用元信息':'Metadaten übernehmen','下载歌词':'Liedtext herunterladen','未发现本地音乐':'Keine lokale Musik gefunden',
    '停止识别':'Erkennung stoppen','需要麦克风权限':'Mikrofonberechtigung erforderlich','录音仅在内存中用于生成音频指纹，不会保存或上传原始音频。':'Die Aufnahme wird nur im Arbeitsspeicher zur Erstellung eines Audio-Fingerabdrucks verwendet. Das Originalaudio wird weder gespeichert noch hochgeladen.','授权并开始识别':'Berechtigen und Erkennung starten','正在准备麦克风':'Mikrofon wird vorbereitet','录音不会保存到设备':'Die Aufnahme wird nicht auf dem Gerät gespeichert','正在聆听':'Hört zu','请靠近声音来源，并保持周围环境安静':'Gehe näher an die Audioquelle und halte die Umgebung möglichst ruhig.','正在寻找这首歌':'Titel wird gesucht','马上就好，请继续让音乐播放':'Fast geschafft – lass die Musik weiterlaufen.','暂未识别到歌曲':'Noch kein Titel erkannt','可以让手机更靠近声音来源，或换到安静一点的环境再试一次。':'Halte das Smartphone näher an die Audioquelle oder versuche es in einer ruhigeren Umgebung erneut.','重新识别':'Erneut erkennen','识别失败':'Erkennung fehlgeschlagen','已停止识别':'Erkennung gestoppt','准备好后，可以再次开始识别。':'Du kannst die Erkennung jederzeit erneut starten.','只会向识别接口发送音频指纹':'An den Erkennungsdienst wird nur der Audio-Fingerabdruck gesendet','识别到 ${songs.size} 首歌曲':'${songs.size} Titel erkannt','查看网易云详情':'Details bei NetEase ansehen',
    '初始设置':'Ersteinrichtung','上一步':'Zurück','继续':'Weiter','跳过':'Überspringen','选择要启用的音源':'Musikquellen auswählen','至少选择一个音源，之后可以逐一登录；这些设置也可以稍后在设置中修改。':'Wähle mindestens eine Musikquelle. Du kannst dich anschließend einzeln anmelden und die Auswahl später in den Einstellungen ändern.','音源正在初始化':'Musikquelle wird initialisiert','将启用此音源':'Diese Musikquelle wird aktiviert','不会加载此音源':'Diese Musikquelle wird nicht geladen','Bilibili 仅作为替换音源':'Bilibili nur als Ersatzquelle verwenden','不在搜索和首页展示，只在原音源资源不可用时参与智能替换。':'Nicht in Suche und Startseite anzeigen; nur für intelligenten Ersatz verwenden, wenn die ursprüngliche Quelle nicht verfügbar ist.','请至少':'Bitte mindestens','选择应用主题':'App-Design auswählen','之后仍可在设置中修改。':'Kann später in den Einstellungen geändert werden.','外观模式':'Darstellung','配色方案':'Farbschema','封面动态取色':'Dynamische Farben aus dem Cover','根据当前播放封面生成播放器主题色':'Player-Farben aus dem aktuell abgespielten Cover erzeugen','选择默认音质':'Standard-Audioqualität auswählen','可以分别设置 Wi‑Fi 和蜂窝网络下的播放音质。':'Die Audioqualität kann für WLAN und Mobilfunk getrennt eingestellt werden.','蜂窝网络':'Mobilfunk','已登录：$it':'Angemeldet: $it','已登录':'Angemeldet','登录可使用个性化推荐、我的歌单等功能；也可以先跳过。':'Mit Anmeldung stehen personalisierte Empfehlungen, eigene Playlists und weitere Funktionen zur Verfügung. Du kannst dies auch überspringen.','退出登录':'Abmelden','网页登录':'Web-Anmeldung','使用 Cookie 登录':'Mit Cookie anmelden','使用 Headers 登录':'Mit Headern anmelden','导入 ytmusic_header.json':'ytmusic_header.json importieren','使用 Google Cloud「TVs and Limited Input devices」类型的 OAuth 客户端。':'Google-Cloud-OAuth-Client vom Typ „TVs and Limited Input devices“ verwenden.','使用 Google 登录（TV）':'Mit Google anmelden (TV)','导入 client_secret.json / oauth.json':'client_secret.json / oauth.json importieren','浏览器已打开，请输入下方验证码':'Der Browser wurde geöffnet. Gib dort den folgenden Code ein.','设备验证码':'Gerätecode','复制验证码':'Code kopieren','重新打开浏览器':'Browser erneut öffnen','取消授权':'Autorisierung abbrechen',
    '音频信息':'Audioinformationen','当前格式':'Aktuelles Format','编码':'Codec','平均比特率':'Durchschnittliche Bitrate','峰值比特率':'Spitzen-Bitrate','解码方式':'Decodierung','软件解码':'Software-Decodierung','硬件解码':'Hardware-Decodierung','解码器':'Decoder','替换音频':'Audio ersetzen','来源':'Quelle','策略':'Strategie','匹配度':'Übereinstimmung','候选音源':'Alternative Quellen','候选查询失败：${candidateState.errorMessage}':'Abfrage der Alternativen fehlgeschlagen: ${candidateState.errorMessage}','暂无符合条件的候选音源':'Keine passende alternative Quelle gefunden','${sourceLabel(candidate.track, null)} · 匹配度 ${formatSmartReplacementScore(candidate.score)}':'${sourceLabel(candidate.track, null)} · Übereinstimmung ${formatSmartReplacementScore(candidate.score)}','已选':'Ausgewählt','歌曲详情':'Titeldetails',
    '收起播放器':'Player minimieren','正在播放':'Wird abgespielt','播放队列':'Warteschlange','未播放':'Nicht abgespielt','$queueSize 首':'$queueSize Titel','清空队列':'Warteschlange leeren','清空播放队列':'Wiedergabewarteschlange leeren','确定要清空播放队列吗？当前播放的歌曲将保留。':'Wiedergabewarteschlange wirklich leeren? Der aktuell abgespielte Titel bleibt erhalten.','当前播放':'Aktuell','接下来播放':'Als Nächstes','队列后续':'Später in der Warteschlange','从队列移除':'Aus Warteschlange entfernen','睡眠定时':'Sleep-Timer','睡眠定时，剩余 ${runtimeFormatSleepTimerRemaining(timerState.remainingMs ?: 0L)}':'Sleep-Timer, noch ${runtimeFormatSleepTimerRemaining(timerState.remainingMs ?: 0L)}','当前曲目结束后暂停':'Nach aktuellem Titel pausieren','选择自动暂停播放的时间':'Zeitpunkt für automatisches Pausieren wählen','按时长':'Nach Dauer','播放结束':'Nach Wiedergabeende','自定义睡眠定时':'Benutzerdefinierter Sleep-Timer','时长（分钟）':'Dauer (Minuten)','例如 45':'z. B. 45','请输入 $SLEEP_TIMER_MIN_MINUTES–$SLEEP_TIMER_MAX_MINUTES 分钟':'Bitte $SLEEP_TIMER_MIN_MINUTES–$SLEEP_TIMER_MAX_MINUTES Minuten eingeben','还有 ${runtimeFormatSleepTimerRemaining(timerState.remainingMs ?: 0L)}':'Noch ${runtimeFormatSleepTimerRemaining(timerState.remainingMs ?: 0L)}','时间到后自动暂停播放':'Nach Ablauf automatisch pausieren','播放完当前曲目的全部内容后暂停':'Nach vollständiger Wiedergabe des aktuellen Titels pausieren','+5 分钟':'+5 Minuten','关闭定时':'Timer ausschalten','$minutes 分钟':'$minutes Minuten','自定义':'Benutzerdefiniert','包含当前曲目的全部多段内容':'Alle Teile des aktuellen Titels einschließen','${minutes / 60L}小时${minutes % 60L}分':'${minutes / 60L} Std. ${minutes % 60L} Min.','${minutes}分${seconds}秒':'${minutes} Min. ${seconds} Sek.','${seconds}秒':'${seconds} Sek.','正在加载音频':'Audio wird geladen','选择一首音乐开始播放':'Wähle einen Titel zum Abspielen','上一首':'Vorheriger Titel','下一首':'Nächster Titel','不喜欢':'Gefällt mir nicht','清空':'Leeren',
    '${displayFeature.providerName} · $contentCount 项':'${displayFeature.providerName} · $contentCount Einträge','本期暂无内容':'Derzeit keine Inhalte','$it 首':'$it Titel','删除歌单？':'Playlist löschen?','播放 MV':'Musikvideo abspielen','添加到歌单':'Zur Playlist hinzufügen','相似歌曲':'Ähnliche Titel','热评':'Top-Kommentare','匿名用户':'Anonymer Benutzer','$it 张专辑':'$it Alben','暂无专辑':'Keine Alben',
    '音源与账号':'Musikquellen & Konten','音源启用、排序、登录与显示范围':'Musikquellen aktivieren, sortieren, anmelden und Sichtbarkeit festlegen','播放与音质':'Wiedergabe & Audioqualität','网络音质、播放策略与智能替换':'Netzwerkqualität, Wiedergabestrategie und intelligenter Ersatz','外观与显示':'Darstellung & Anzeige','主题、歌词字号与歌词同步':'Design, Liedtextgröße und Liedtextsynchronisierung','本地音乐':'Lokale Musik','媒体目录扫描与短音频过滤':'Medienordner scannen und kurze Audiodateien filtern','下载与存储':'Downloads & Speicher','下载行为、缓存上限与清理':'Downloadverhalten, Cache-Limits und Bereinigung','关于':'Über die App','版本、项目链接与诊断信息':'Version, Projektlinks und Diagnoseinformationen','主题设置':'Design-Einstellungen','登录凭证':'Anmeldedaten','音源账号':'Musikquellen-Konto','该音源当前不可用':'Diese Musikquelle ist derzeit nicht verfügbar','音源':'Musikquellen','长按拖动排序${provider.providerName}':'${provider.providerName} zum Sortieren lange drücken und ziehen','配置${provider.providerName}':'${provider.providerName} konfigurieren','管理${provider.providerName}账号':'${provider.providerName}-Konto verwalten','备份与恢复':'Sichern & Wiederherstellen','说明':'Hinweis','长按拖动调整音源优先级':'Lange drücken und ziehen, um die Priorität der Musikquellen zu ändern','配置按钮单独管理搜索、推荐、探索和“我的”显示范围；账号按钮用于登录与授权。':'Über „Konfigurieren“ legst du separat fest, wo eine Quelle in Suche, Empfehlungen, Entdecken und „Meine Musik“ erscheint. Über „Konto“ verwaltest du Anmeldung und Autorisierung.','备份':'Sicherung','导出全部':'Alle exportieren','${it.size} 个音源':'${it.size} Musikquellen','单独导出':'Einzeln exportieren','暂无已登录音源':'Keine angemeldeten Musikquellen','恢复':'Wiederherstellen','从文件恢复':'Aus Datei wiederherstellen','显示范围':'Sichtbarkeit','已显示':'Sichtbar','已隐藏':'Ausgeblendet','未显示':'Nicht sichtbar','音质':'Audioqualität','连接 Wi‑Fi 时优先使用的音质':'Bevorzugte Audioqualität über WLAN','使用移动数据时优先使用的音质':'Bevorzugte Audioqualität über Mobilfunk','播放策略':'Wiedergabeverhalten','其他应用播放时自动暂停':'Bei Wiedergabe durch andere Apps automatisch pausieren','检测到其他应用开始播放时暂停当前播放':'Aktuelle Wiedergabe pausieren, wenn eine andere App Audio startet','资源不可用时':'Wenn eine Quelle nicht verfügbar ist','宽松':'Locker','平衡':'Ausgewogen','严格':'Streng','智能替换':'Intelligenter Ersatz','原音源无法播放时，从所选音源中搜索并匹配可播放版本':'Wenn die ursprüngliche Quelle nicht abspielbar ist, in ausgewählten Quellen nach einer passenden Version suchen','当前资源不可用策略不是“智能替换”，切换后以下设置生效':'Die aktuelle Strategie ist nicht „Intelligenter Ersatz“. Die folgenden Einstellungen gelten nach dem Umschalten.','替换音源':'Ersatzquellen','没有已启用的音源':'Keine aktivierten Musikquellen','仅启用一个音源时无法跨源替换；播放时会自动排除歌曲自身来源':'Bei nur einer aktivierten Quelle ist kein quellenübergreifender Ersatz möglich. Die ursprüngliche Quelle wird bei der Suche automatisch ausgeschlossen.','播放时会自动排除歌曲自身来源；候选音源顺序沿用上方音源排序':'Die ursprüngliche Quelle wird automatisch ausgeschlossen. Die Reihenfolge der Ersatzquellen folgt der obigen Sortierung.','匹配严格度':'Übereinstimmungsgenauigkeit','最低匹配分':'Mindestwert für Übereinstimmung','分数越高匹配越严格':'Je höher der Wert, desto strenger die Übereinstimmung','主题':'Design','主题模式':'Designmodus','选择浅色、深色或跟随系统':'Hell, dunkel oder Systemeinstellung wählen','${settings.themeColorScheme.label} · 调色板与色彩规范':'${settings.themeColorScheme.label} · Farbpalette und Farbspezifikation','播放显示':'Wiedergabeanzeige','歌词字号':'Liedtextgröße','状态栏歌词':'Liedtext in der Statusleiste','通过词幕在系统状态栏显示当前歌词':'Aktuellen Liedtext über die Lyrics-Anzeige in der Systemstatusleiste anzeigen','比亚迪仪表歌词':'BYD-Instrumenten-Liedtext','将当前歌词同步到驾驶仪表的三行歌词区域':'Aktuellen Liedtext mit dem dreizeiligen Liedtextbereich des Fahrzeuginstruments synchronisieren','颜色':'Farben','强调色':'Akzentfarbe','选择应用主要颜色方案':'Primäres Farbschema der App auswählen','调色板风格':'Palettenstil','Material 3 动态配色算法风格':'Stil des Material-3-Algorithmus für dynamische Farben','色彩规范':'Farbspezifikation','选择 Material 3 色彩规范版本':'Version der Material-3-Farbspezifikation auswählen','导航':'Navigation','预测性返回手势':'Vorausschauende Zurück-Geste','返回手势过程中预览上一页':'Während der Zurück-Geste die vorherige Seite anzeigen','封面':'Cover','播放时根据当前封面调整界面颜色':'Oberflächenfarben während der Wiedergabe an das aktuelle Cover anpassen','扫描设置':'Scan-Einstellungen','忽略短音频':'Kurze Audiodateien ignorieren','扫描时过滤低于指定时长的音频':'Audiodateien unterhalb der festgelegten Dauer beim Scannen ausfiltern','不过滤':'Nicht filtern','${state.localMusic.minDurationSeconds} 秒':'${state.localMusic.minDurationSeconds} Sekunden','$it 秒':'$it Sekunden','媒体目录':'Medienordner','暂无可用目录':'Keine verfügbaren Ordner','刷新本地音乐后将在这里显示媒体目录':'Nach dem Aktualisieren der lokalen Musik werden die Medienordner hier angezeigt.','${directory.trackCount} 首':'${directory.trackCount} Titel','${state.downloadTasks.count { it.status == DownloadTaskStatus.Downloading }} 个下载中':'${state.downloadTasks.count { it.status == DownloadTaskStatus.Downloading }} laufende Downloads','并行下载数量':'Parallele Downloads','同时进行的下载任务数量':'Anzahl gleichzeitig laufender Downloadaufträge','缓存':'Cache','音频缓存上限':'Audio-Cache-Limit','本地音频缓存最大占用空间':'Maximaler Speicherplatz für den lokalen Audio-Cache','图片缓存上限':'Bild-Cache-Limit','封面等图片缓存最大占用空间':'Maximaler Speicherplatz für Cover und andere Bilder','当前缓存':'Aktueller Cache','清理':'Bereinigen','应用信息':'App-Informationen','版本':'Version','版本 ':'Version ','FuoEvolve 源代码':'FuoEvolve-Quellcode','GitHub 项目主页':'GitHub-Projektseite','FeelUOwn 主项目':'FeelUOwn-Hauptprojekt','上游项目主页':'Upstream-Projektseite','诊断':'Diagnose','应用日志':'App-Protokolle','查看调试日志与错误信息':'Debug-Protokolle und Fehlerinformationen anzeigen','账号状态':'Kontostatus','未登录':'Nicht angemeldet','导出登录凭证':'Anmeldedaten exportieren','登录方式':'Anmeldemethode','使用 Google Cloud「TVs and Limited Input devices」类型的 OAuth 客户端，可导入 client_secret.json / oauth.json。':'Google-Cloud-OAuth-Client vom Typ „TVs and Limited Input devices“ verwenden; client_secret.json / oauth.json kann importiert werden.','${catalog.enabledProviderIds.size} 个音源已启用 · $loggedIn 个已登录':'${catalog.enabledProviderIds.size} Musikquellen aktiviert · $loggedIn angemeldet','${catalog.enabledProviderIds.size} 个音源已启用':'${catalog.enabledProviderIds.size} Musikquellen aktiviert','${settings.localMusic.directories.size} 个媒体目录':'${settings.localMusic.directories.size} Medienordner','并行下载 ${settings.settings.downloadParallelism} · 缓存与清理':'${settings.settings.downloadParallelism} parallele Downloads · Cache und Bereinigung','已登录 · $it':'Angemeldet · $it','已启用':'Aktiviert','未启用':'Deaktiviert',
}

files = [
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/home/HomeScreen.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/settings/SettingsFeatureScreen.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/search/SearchScreen.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/onboarding/OnboardingFeatureScreen.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/download/DownloadManagerScreen.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/localmusic/LocalMusicSection.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/localplaylist/LocalPlaylistScreen.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/playback/RuntimeFullPlayer.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/playback/RuntimeMiniPlayer.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/playback/PlayerDialogs.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/home/MineHomeFeatureSection.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/home/ProviderHomeFeatureSection.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/recognition/AudioRecognitionScreen.kt',
    'shared/src/commonMain/kotlin/org/feeluown/mobile/feature/provider/ProviderDetailRoutes.kt',
]

for filename in files:
    path = Path(filename)
    text = path.read_text(encoding='utf-8')
    for src, dst in sorted(T.items(), key=lambda kv: len(kv[0]), reverse=True):
        text = text.replace(src, dst)
    path.write_text(text, encoding='utf-8')

Path('androidApp/src/main/res/values/strings.xml').write_text(
    '<resources>\n'
    '    <string name="close">Schließen</string>\n'
    '    <string name="done">Fertig</string>\n'
    '    <string name="provider_login_cookie_hint">Nach erfolgreicher Anmeldung wird das Cookie automatisch übernommen.</string>\n'
    '    <string name="provider_browser_login_title">%1$s – Browser-Anmeldung</string>\n'
    '</resources>\n',
    encoding='utf-8',
)

workflow = Path('.github/workflows/android-apk.yml')
w = workflow.read_text(encoding='utf-8')
if '  build-debug-apk:\n' not in w:
    debug = '''  build-debug-apk:
    name: Build debug APK
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout
        uses: actions/checkout@v5

      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Build debug APK
        run: ./gradlew :androidApp:assembleDebug

      - name: Upload debug APK
        uses: actions/upload-artifact@v7
        with:
          name: fuo-evolve-german-debug-${{ github.sha }}
          path: androidApp/build/outputs/apk/debug/*.apk
          archive: false
          if-no-files-found: error

'''
    w = w.replace('  build-release-apks:\n', debug + '  build-release-apks:\n', 1)
if "    if: github.repository == 'feeluown/FuoEvolve'\n    name: Build signed multi-ABI release APK" not in w:
    w = w.replace(
        '  build-release-apks:\n    name: Build signed multi-ABI release APK\n',
        "  build-release-apks:\n    if: github.repository == 'feeluown/FuoEvolve'\n    name: Build signed multi-ABI release APK\n",
        1,
    )
workflow.write_text(w, encoding='utf-8')

remaining = []
for filename in files:
    text = Path(filename).read_text(encoding='utf-8')
    for match in re.finditer(r'"([^"\\]*(?:\\.[^"\\]*)*)"', text):
        if re.search(r'[\u3400-\u9fff]', match.group(1)):
            remaining.append((filename, match.group(1)))
if remaining:
    for filename, value in remaining:
        print(f'{filename}: {value}')
    raise SystemExit('German localization incomplete in selected test surfaces')
print(f'Localized {len(files)} shared UI files plus Android native strings.')
