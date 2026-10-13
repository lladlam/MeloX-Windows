import subprocess, sys
from pathlib import Path

repo=Path('/home/lladlam/项目/MeloX-Windows')
gradle='/home/lladlam/.gradle/wrapper/dists/gradle-9.5.1-bin/bvvv0hnjcinfezlom7fy9dbyi/gradle-9.5.1/bin/gradle'
base=[gradle, ':desktop:captureUi', '--no-daemon','--max-workers=1','--console=plain','-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttp.nonProxyHosts=localhost|127.*']
home=sys.argv[2] if len(sys.argv)>2 else '/tmp/opencode/melox-settings-onboarding-home'
name=sys.argv[1]
if name=='onboarding':
    steps='privacy:tap@onboarding-privacy;privacy-close:tap@legal-document-close;disclaimer:tap@onboarding-disclaimer;disclaimer-close:tap@legal-document-close;agree:tap@onboarding-agree;later:tap@onboarding-later;cloud-policy:tap@remote-consent-policy;cloud-close:tap@legal-document-close;reject:tap@remote-reject'
    expect='00-settings=tag:onboarding-legal,onboarded:false,cloud-choice:false;privacy=full-tag:legal-document,state:legal-document:privacy;agree=tag:onboarding-connect,absent:remote-consent,onboarded:false,legal-consent:1.2-2026-08-27;later=tag:remote-consent,onboarded:true,cloud-choice:false;cloud-policy=full-tag:legal-document,state:legal-document:cloud;reject=absent:remote-consent,cloud-choice:true,cloud-enabled:false,tag:settings-root'
elif name=='legal':
    steps='search:text@settings-search,legal;open:tap@settings-open-legal;privacy:tap@legal-open-privacy;privacy-close:tap@legal-document-close;disclaimer:tap@legal-open-disclaimer;disclaimer-close:tap@legal-document-close;third-party:tap@legal-open-third-party;third-party-close:tap@legal-document-close;consent:tap@legal-remote-enabled;reject:tap@remote-reject;back:escape;empty:text@settings-search,no-such-setting;keyword:text@settings-search,lossless'
    expect='00-settings=absent:onboarding-legal,absent:remote-consent,onboarded:true,cloud-choice:true;search=tag:settings-open-legal;open=tag:settings-legal,state:legal-version:1.2-2026-08-27;privacy=full-tag:legal-document,state:legal-document:privacy;disclaimer=state:legal-document:disclaimer;third-party=state:legal-document:third-party;consent=tag:remote-consent;reject=unchecked:legal-remote-enabled,toggle-position:legal-remote-enabled:0,cloud-enabled:false;empty=tag:settings-search-empty;keyword=tag:settings-open-playback,absent:settings-open-legal'
elif name=='privacy':
    steps='search:text@settings-search,privacy;open:tap@settings-open-privacy;algorithm:tap@privacy-algorithm;policy:tap@privacy-consent-action;early:tap@privacy-policy-agree;ready:wait@10500;agree:tap@privacy-policy-agree;withdraw:tap@privacy-consent-action;clear:tap@privacy-clear;cancel:tap@privacy-clear-cancel;clear-again:tap@privacy-clear;confirm:tap@privacy-clear-confirm;back:escape'
    expect='00-settings=absent:onboarding-legal,absent:remote-consent;open=tag:settings-privacy,personalization:false;algorithm=checked:privacy-algorithm,algorithm:true;policy=tag:privacy-policy,state:privacy-policy-agree:waiting;early=personalization:false,tag:privacy-policy;ready=state:privacy-policy-agree:ready;agree=personalization:true,absent:privacy-policy,state:privacy-content:consented;withdraw=personalization:false,state:privacy-content:not-consented;clear=tag:privacy-clear-dialog;cancel=absent:privacy-clear-dialog;confirm=personalization:false,absent:privacy-clear-dialog;back=tag:settings-root'
elif name=='light':
    steps='search:text@settings-search,legal;open:tap@settings-open-legal;scroll:scroll@260,450,3;policy:tap@legal-open-cloud;close:tap@legal-document-close;back:escape;privacy-search:text@settings-search,privacy;privacy-open:tap@settings-open-privacy'
    expect='00-settings=absent:onboarding-legal;open=tag:settings-legal;policy=full-tag:legal-document,state:legal-document:cloud;privacy-open=tag:settings-privacy'
elif name=='login':
    steps='agree:tap@onboarding-agree;login:tap@onboarding-login;reject:tap@remote-reject;close:tap@account-dialog-close;back:escape'
    expect='00-settings=tag:onboarding-legal;agree=tag:onboarding-connect;login=tag:remote-consent,onboarded:true;reject=tag:account-form-netease,tag:account-screen,cloud-choice:true,cloud-enabled:false;close=absent:account-form-netease;back=tag:settings-root,absent:account-screen'
else:
    raise ValueError(name)
out=f'parity/3f877ea/evidence/settings-{name}-ui'
args=f'--source Local --start-tab Settings --theme {"Light" if name=="light" else "Dark"} --output {repo/out} --wait-ms 500 --startup-wait-ms 700 --frames-ms 0,160,400 --steps {steps} --expect {expect}'
command=base+[f'-PcaptureHome={home}',f'-PcaptureArgs={args}']
(repo/f'{out}-command.txt').write_text(subprocess.list2cmdline(command)+'\n')
with (repo/f'{out}.log').open('w') as log:
    result=subprocess.run(command,cwd=repo,stdout=log,stderr=subprocess.STDOUT)
print(name, result.returncode)
sys.exit(result.returncode)
