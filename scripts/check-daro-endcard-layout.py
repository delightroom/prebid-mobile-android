#!/usr/bin/env python3
"""Render the actual Java test's HTML through viewport changes using isolated headless Chrome."""
import argparse
import html
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--chrome", default="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
args = parser.parse_args()
source = Path(__file__).resolve().parents[1] / "PrebidMobile/PrebidMobile-core/build/reports/daro-endcard/layout.html"
if not source.exists():
    raise SystemExit("Run CreativeModelsMakerVastTest before this viewport gate.")
creative = re.sub(r'src="[^"]*"', 'src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs="', source.read_text())
script = """
const frame=document.querySelector('iframe');
const sizes=[[390,844],[844,390],[720,390],[480,390],[600,240],[844,390],[390,844]];
const pause=()=>new Promise(resolve=>setTimeout(resolve,20));
async function run(){
const results=[];
for(let index=0;index<sizes.length;index++){
const [width,height]=sizes[index];frame.style.width=width+'px';frame.style.height=height+'px';
await pause();const doc=frame.contentDocument,win=frame.contentWindow;
doc.documentElement.style.setProperty('--daro-safe-top','24px');
doc.documentElement.style.setProperty('--daro-safe-bottom','34px');
const safeLeft=index===2||index===3?36:0,safeRight=index===2||index===3?20:0;
doc.documentElement.style.setProperty('--daro-safe-left',safeLeft+'px');doc.documentElement.style.setProperty('--daro-safe-right',safeRight+'px');
if(index===4){doc.querySelector('.daro-title').textContent='Long copy '.repeat(40);doc.querySelector('.daro-subtitle').textContent='Description '.repeat(80);}
if(index===5){doc.querySelector('.daro-title').textContent='Skyline Pro';doc.querySelector('.daro-subtitle').textContent='Focus & Productivity';}
await pause();const cta=doc.querySelector('.daro-cta').getBoundingClientRect(),copy=doc.querySelector('.daro-copy').getBoundingClientRect();
const landscape=width>height;
results.push({width,height,cta:[cta.x,cta.y,cta.width,cta.height],copy:[copy.x,copy.y,copy.width,copy.height],
fits:cta.x>=safeLeft&&cta.right<=width-safeRight&&cta.bottom<=height&&(landscape?copy.bottom<=cta.top&&copy.top>=24:true),
correctCta:landscape?Math.abs(cta.width-Math.min(480,width-48-safeLeft-safeRight))<1&&cta.height===52&&cta.bottom===height-66:cta.width===326&&cta.height===60,
scrollable:index!==4||doc.querySelector('.daro-copy').scrollHeight>copy.height});
}
document.documentElement.dataset.results=JSON.stringify(results);
}frame.addEventListener('load',run);
"""
with tempfile.TemporaryDirectory(prefix="daro-endcard-layout-") as directory:
    directory = Path(directory)
    page = directory / "runner.html"
    page.write_text('<!doctype html><iframe srcdoc="'+html.escape(creative, quote=True)+'"></iframe><script>'+script+'</script>')
    output = directory / "output.html"
    with output.open("w") as stdout, (directory / "chrome.log").open("w") as stderr:
        process = subprocess.Popen([args.chrome,"--headless=new","--no-first-run","--no-default-browser-check",
            "--disable-background-networking","--disable-component-update", "--user-data-dir="+str(directory / "profile"),
            "--dump-dom", "--virtual-time-budget=3000", page.as_uri()], stdout=stdout, stderr=stderr, start_new_session=True)
        deadline = time.monotonic()+30
        match = None
        while time.monotonic()<deadline:
            match = re.search(r'data-results="([^"]*)"',output.read_text())
            if match or process.poll() is not None: break
            time.sleep(.1)
        if process.poll() is None:
            os.killpg(process.pid,signal.SIGTERM)
            process.wait(timeout=5)
    if not match:
        raise SystemExit("Headless HTML gate did not produce a result: "+(directory/"chrome.log").read_text()[-2000:])
    results = json.loads(html.unescape(match.group(1)))
    print(json.dumps(results, indent=2))
    assert all(result["fits"] and result["correctCta"] and result["scrollable"] for result in results), "EndCard overlaps or clips controls during resize"
print("PASS portrait -> landscape variants -> short viewport/long copy -> portrait")
