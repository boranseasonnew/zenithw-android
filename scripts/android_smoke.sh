#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-results
find smoke-results -type f -delete
MODE="${1:-full}"
trap 'adb logcat -d > smoke-results/logcat.txt; adb pull /sdcard/Android/data/space.zenithw.app.stable/files/smoke-ui smoke-results/ || true' EXIT
./gradlew --no-daemon :app:assembleReleaseAndroidTest
# The current host adb can stall streaming an APK to the API 24 package manager.
# Use push-based installation and bound adb commands so failures retain diagnostics.
timeout 60 adb wait-for-device
timeout 60 adb shell pm path android | grep -q '^package:'
timeout 180 adb install --no-streaming -r app/build/outputs/apk/release/app-x86_64-release.apk
TEST_APK="$(find app/build/outputs/apk/androidTest/release -name '*.apk' -print -quit)"
test -n "$TEST_APK"
timeout 180 adb install --no-streaming -r "$TEST_APK"
PACKAGE="space.zenithw.app.stable"
TEST_PACKAGE="$PACKAGE.test"
FAILED_TESTS=()
run_test() {
  if adb shell am instrument -w -r -e class "space.zenithw.app.${2:-ReleaseSmokeTest}#$1" \
    "$TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner" | tee "smoke-results/$1.txt" \
    && grep -q 'OK (1 test)' "smoke-results/$1.txt"; then
    return 0
  fi
  FAILED_TESTS+=("${2:-ReleaseSmokeTest}#$1")
  echo "FAILED: ${FAILED_TESTS[-1]} (continuing to collect independent test results)"
}
require_all_tests_passed() {
  if [ "${#FAILED_TESTS[@]}" -gt 0 ]; then
    printf 'FAILED TEST: %s\n' "${FAILED_TESTS[@]}"
    return 1
  fi
}
run_test saveSettings
adb shell am force-stop "$PACKAGE"
run_test restoreSettings
if [ "$MODE" != "engine-only" ]; then
  run_test languages
  run_test settingsCategories
  run_test settingsKeepsScrollWhenReturningFromCategory
  run_test singleMediaPlaylistSelection
  run_test bulkLinkDraft
  run_test bulkWorkspace
  run_test sealWorkspaceControls
  for method in metadataOffersRealFormatsAndSubtitleLanguages privacyRetainsActiveQueueAndClearsPerMediaDefaults configurationIsAvailableWithoutWaitingForExtraction requestHonorsAudioFormatClipAndNetworkOptions; do
    run_test "$method" SealFlowTest
  done
  for method in extractedArtworkAndDurationSurviveHistoryReload cookieRequestUsesCapturedBrowserAgentAndAllowsExplicitOverride singleVideoWithPlaylistDefaultStaysOnHomeAndQueuesOneItem privateArtworkIsResizedAndCanBeReadAfterRecreatingTheStore; do
    run_test "$method" DownloadRegressionTest
  done
  for method in completePasteOpensPanelWithoutNetworkOrLosingOptions latestOfFiveLinksWinsAndUserChoicesSurvive timingLogsContainNoMetadataOrCredentials concurrentIdenticalRequestsShareOneExtraction cancellationAndInvalidInputHaveExplicitStates; do
    run_test "$method" MetadataOptimizationTest
  done
  for method in repeatAnalyzeAndStaleDismissPreserveNewPanel completedCardsDisappearButFailuresAndActiveDownloadsRemain archivedVideoIsSkippedWithoutAttemptingPublication; do
    run_test "$method" MetadataOptimizationTest
  done
  run_test captureHomeDownloadCard
fi
for method in realFormatsCacheAvoidsNetworkAndPreservesQueryIdentity cancellationUnblocksReplacementAndDoesNotLeaveWorkerBusy sourceFailureRetainsRuntimeAndPlaylistCacheAvoidsAnotherRequest; do
  run_test "$method" MetadataOptimizationTest
done
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
if [ "$MODE" = "engine-only" ]; then
  require_all_tests_passed
  echo 'PASS: engine archive, live updates, settings and nightly restart persistence'
  exit 0
fi
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
for label in ('Zenith','Yapıştır','İndir','Ayarlar','Toplu indirme ve komutlar'):
    assert label in labels, (label,labels)
for slogan in ('Bir bağlantı.', 'Hepsi senin.', 'DAHA AZ ADIM','Bağlantını getir.','Video, ses ve oynatma listelerini cihazında indir.'):
    assert not any(slogan in label for label in labels), labels
import re
bounds=lambda node: list(map(int,re.findall(r'\d+',node.get('bounds',''))))
url=next(node for node in nodes if node.get('class')=='android.widget.EditText')
logo=next(node for node in nodes if node.get('text')=='Zenith')
root_bounds=bounds(nodes[0])
y=bounds(url)
ratio=(y[1]+y[3])/2/root_bounds[3]
assert .35<ratio<.70, ('URL must be in the central region',ratio)
assert bounds(logo)[3]<root_bounds[3]*.25, 'Logo must stay at top left'
print('PASS: five languages, persistence and centered minimal screen on Android 13; URL ratio',ratio)
PY
require_all_tests_passed
