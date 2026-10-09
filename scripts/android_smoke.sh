#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-results
trap 'adb logcat -d > smoke-results/logcat.txt; adb pull /sdcard/Android/data/space.zenithw.app.stable/files/smoke-ui smoke-results/ || true' EXIT
./gradlew --no-daemon :app:assembleReleaseAndroidTest
adb install -r app/build/outputs/apk/release/app-x86_64-release.apk
TEST_APK="$(find app/build/outputs/apk/androidTest/release -name '*.apk' -print -quit)"
test -n "$TEST_APK"
adb install -r "$TEST_APK"
PACKAGE="space.zenithw.app.stable"
TEST_PACKAGE="$PACKAGE.test"
run_test() {
  adb shell am instrument -w -r -e class "space.zenithw.app.${2:-ReleaseSmokeTest}#$1" \
    "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner" | tee "smoke-results/$1.txt"
  grep -q 'OK (1 test)' "smoke-results/$1.txt"
}
run_test saveSettings
adb shell am force-stop "$PACKAGE"
run_test restoreSettings
run_test languages
run_test settingsCategories
for method in pythonPreambleIsHandledBeforeZipParsing ordinaryZipIsAlsoReadable realBundledZipappIsReadable malformedArchivesHaveActionableErrors oversizedVersionFileIsRejected; do
  run_test "$method" EngineArchiveTest
done
for method in releasePageFailureUsesAssetRoute blockedRedirectsUseOfficialApi blockedAssetUsesApiDownloadAndKeepsVersion untrustedRedirectIsNeverRequested oversizedResponseIsRejectedAndCacheIsDisabled failureNamesLookupStageAndHttpStatus corruptUpdatePreservesWorkingEngine mismatchedVersionKeepsWorkingEngineAndNamesBothVersions; do
  run_test "$method" EngineUpdateSourceTest
done
run_test updateChannels
run_test saveNightlyEngineForRestart
adb shell am force-stop "$PACKAGE"
run_test restoreNightlyEngineAfterRestart
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/space.zenithw.app.MainActivity"
sleep 2
adb exec-out screencap -p > smoke-results/home.png
adb shell uiautomator dump /sdcard/zenith-ui.xml
adb pull /sdcard/zenith-ui.xml smoke-results/home.xml
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke-results/home.xml').getroot().iter('node'))
labels=[node.get('text','') or node.get('content-desc','') for node in nodes]
for label in ('Zenith','Yapıştır','İndir','Ayarlar'):
    assert label in labels, (label,labels)
for slogan in ('Bir bağlantı.', 'Hepsi senin.', 'DAHA AZ ADIM'):
    assert not any(slogan in label for label in labels), labels
import re
bounds=lambda node: list(map(int,re.findall(r'\d+',node.get('bounds',''))))
url=next(node for node in nodes if node.get('class')=='android.widget.EditText')
logo=next(node for node in nodes if node.get('content-desc')=='Zenith')
root_bounds=bounds(nodes[0])
y=bounds(url)
ratio=(y[1]+y[3])/2/root_bounds[3]
assert .45<ratio<.70, ('URL must be near/slightly below center',ratio)
assert bounds(logo)[3]<root_bounds[3]*.25, 'Logo must stay at top left'
print('PASS: five languages, persistence and centered minimal screen on Android 13; URL ratio',ratio)
PY
