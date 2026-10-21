import os
from shutil import copytree
import sys
from urllib.request import urlopen, urlretrieve
import json
from pathlib import Path
import argparse

def count_lines(filename):
  """Counts the number of lines in translation."""
  if not os.path.exists(filename):
    return 0
  with open(filename, 'r') as f:
    return sum(1 for line in f)

def copy_directory(src, dst, line_threshold):
  """Copies a directory if the strings.xml file has enough lines."""
  xml_file = os.path.join(src, "strings.xml")
  line_count = count_lines(xml_file)
  if line_count >= line_threshold:
    copytree(src, dst, dirs_exist_ok=True)
    print(f"Copied directory {src} to {dst} (lines: {line_count})")

translations_url = f"https://hosted.weblate.org/api/components/tor/tor-vpn/translations/"

line_threshold = 150  # Change this to your desired minimum

# cd to repo root
os.chdir(os.path.dirname(sys.argv[0]))
if (os.path.basename(os.getcwd()) == "scripts"):
  os.chdir("..")

parser = argparse.ArgumentParser(
  prog="get-l10n",
)
parser.add_argument("-f", nargs=1, help="filename base to read instead of calling weblate, eg: debs\\weblate will start with debs\\weblate.1.json")
parser.add_argument("sourceDir", help="base directory that https://gitlab.torproject.org/tpo/translation.git is checkout out to")
args = parser.parse_args()

source_dir = args.sourceDir

results = {}

if args.f:
  fileformat = "{}.{}.json".format(args.f[0], "{}")
  fileNum = 1
  filename = fileformat.format(fileNum)
  print("Reading translation report...")
  if not Path(filename).exists():
    print("Error: {} does not exist".format(filename))
    exit(-1)

  while Path(filename).exists():
    print("  reading {}...".format(filename))
    file = open(filename)
    translations = json.load(file)
    file.close()

    for t in translations["results"]:
      results[t['language_code']] = t

    fileNum += 1
    filename = fileformat.format(fileNum)
else:
  print("Downloading translation report...")

  while translations_url != None:
    print("  downloading {}...".format(translations_url))
    with urlopen(translations_url) as response:
      try:
        body = response.read()
        #translations = json.load(response)
        translations = json.loads(body)

        for t in translations["results"]:
          results[t['language_code']] = t
        translations_url = translations['next']
      except ValueError as e:
        print("EXCEPTION: encountered processing JSON from {}:".format(translations_url))
        print("  {}".format(e))
        print("  json received status: '{}'".format(response.status))
        print("  json received reason: '{}'".format(response.reason))
        print("  json received body: {}".format(body))
        exit(-1)

# Loop through subdirectories
for root, _, files in os.walk(source_dir):
  for filename in files:
    if filename == "strings.xml":
      # Construct destination directory with same name as source
      lang = os.path.basename(root)[7:]
      dst_dir = os.path.join("app/src/main/res/", os.path.basename(root))
      exists = Path(dst_dir).exists()
      if (lang not in results) and not exists :
        print("skipping {} as it's not in the report".format(lang))
        break # Only need to check one strings.xml per directory
      t = None
      if lang in results:
        t = results[lang]
        print("Checking language {}... percent complete: {} and failing checks: {}".format(lang,  t['translated_percent'], t['failing_checks']))
      else:
        print("Checking language {}...".format(lang))
      if not exists and (t["language_code"] == "en" or t["translated_percent"] != 100 or t["failing_checks"] != 0):
        print("  skipping")
        break # Only need to check one strings.xml per directory
      print("  copying into repo...")
      copy_directory(root, dst_dir, line_threshold)
      break  # Only need to check one strings.xml per directory

