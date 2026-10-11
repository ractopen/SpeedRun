import urllib.request
import json
import os
import concurrent.futures

os.makedirs("/tmp/mc_libs", exist_ok=True)
manifest_url = "https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json"
data = json.loads(urllib.request.urlopen(manifest_url).read())

def download_lib(lib):
    downloads = lib.get("downloads", {})
    artifact = downloads.get("artifact")
    if not artifact:
        return
    url = artifact.get("url")
    path = artifact.get("path")
    if not url or not path:
        return
    dest = os.path.join("/tmp/mc_libs", os.path.basename(path))
    if not os.path.exists(dest) or os.path.getsize(dest) == 0:
        try:
            urllib.request.urlretrieve(url, dest)
        except Exception as e:
            print(f"Failed {url}: {e}")

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
    executor.map(download_lib, data.get("libraries", []))

print("Libraries downloaded.")
