#!/usr/bin/env python3
"""
Test Re2jSceneNameParser logic against real Xtream API VOD names.
We'll simulate what the parser does step by step.
"""
import json
import re
from collections import Counter

# Load real VOD data
with open('/workspaces/FishIT-Player/test-data/xtream-responses/vod_streams.json', 'r') as f:
    vod_data = json.load(f)

print(f"=== Analyzing {len(vod_data)} VOD items ===\n")

# Pattern categories
pipe_pattern = re.compile(r'^(.+?)\s*\|\s*(\d{4})\s*\|')
scene_pattern = re.compile(r'^(.+?)\.(\d{4})\.')
plain_year = re.compile(r'^(.+?)\s*\((\d{4})\)')

# Classify
pipe_count = 0
scene_count = 0
plain_count = 0
no_year_count = 0

# Sample collections
pipe_samples = []
scene_samples = []
plain_samples = []
no_year_samples = []

for item in vod_data:
    name = item.get('name', '')
    
    if pipe_pattern.match(name):
        pipe_count += 1
        if len(pipe_samples) < 10:
            pipe_samples.append(name)
    elif scene_pattern.match(name):
        scene_count += 1
        if len(scene_samples) < 10:
            scene_samples.append(name)
    elif plain_year.search(name):
        plain_count += 1
        if len(plain_samples) < 10:
            plain_samples.append(name)
    else:
        no_year_count += 1
        if len(no_year_samples) < 10:
            no_year_samples.append(name)

print(f"PATTERN DISTRIBUTION:")
print(f"  Pipe-separated (Title | Year |): {pipe_count} ({100*pipe_count/len(vod_data):.1f}%)")
print(f"  Scene-style (Title.Year.):       {scene_count} ({100*scene_count/len(vod_data):.1f}%)")  
print(f"  Parentheses (Title (Year)):      {plain_count} ({100*plain_count/len(vod_data):.1f}%)")
print(f"  No year detected:                {no_year_count} ({100*no_year_count/len(vod_data):.1f}%)")

print(f"\n=== PIPE-SEPARATED SAMPLES ===")
for s in pipe_samples:
    print(f"  '{s}'")
    
print(f"\n=== SCENE-STYLE SAMPLES ===")
for s in scene_samples:
    print(f"  '{s}'")

print(f"\n=== PARENTHESES SAMPLES ===")
for s in plain_samples:
    print(f"  '{s}'")
    
print(f"\n=== NO-YEAR SAMPLES ===")
for s in no_year_samples[:5]:
    print(f"  '{s}'")

# Now simulate what the parser would extract
print("\n" + "="*60)
print("=== SIMULATING PARSER EXTRACTION ===")
print("="*60)

def simulate_parse(name):
    """Simulate what Re2jSceneNameParser would do."""
    # Pipe pattern: "Title | Year | Rating | Quality"
    pipe_m = re.match(r'^(.+?)\s*\|\s*(\d{4})\s*\|(.*)$', name)
    if pipe_m:
        title = pipe_m.group(1).strip()
        year = int(pipe_m.group(2))
        rest = pipe_m.group(3).strip()
        return {'title': title, 'year': year, 'rest': rest, 'pattern': 'pipe'}
    
    # Scene pattern: "Title.Year.Quality.Etc"
    scene_m = re.match(r'^(.+?)\.(\d{4})\.(.*)$', name)
    if scene_m:
        title = scene_m.group(1).replace('.', ' ').strip()
        year = int(scene_m.group(2))
        rest = scene_m.group(3)
        return {'title': title, 'year': year, 'rest': rest, 'pattern': 'scene'}
    
    # Parentheses pattern: "Title (Year)"
    paren_m = re.search(r'^(.+?)\s*\((\d{4})\)', name)
    if paren_m:
        title = paren_m.group(1).strip()
        year = int(paren_m.group(2))
        return {'title': title, 'year': year, 'rest': '', 'pattern': 'paren'}
    
    # No pattern match - return raw
    return {'title': name, 'year': None, 'rest': '', 'pattern': 'none'}

# Test samples
print("\nPipe-separated parsing:")
for name in pipe_samples[:5]:
    result = simulate_parse(name)
    print(f"  Input:  '{name}'")
    print(f"  Output: title='{result['title']}', year={result['year']}")
    print()

print("\nScene-style parsing:")
for name in scene_samples[:5]:
    result = simulate_parse(name)
    print(f"  Input:  '{name}'")
    print(f"  Output: title='{result['title']}', year={result['year']}")
    print()

# Check for quality tags in pipe-separated names
print("\n" + "="*60)
print("=== QUALITY TAGS IN PIPE-SEPARATED NAMES ===")
print("="*60)

quality_tags = Counter()
for item in vod_data:
    name = item.get('name', '')
    if '|' in name:
        parts = name.split('|')
        for part in parts[2:]:  # After title and year
            tag = part.strip().upper()
            if tag in ['4K', 'HD', 'LOWQ', 'HEVC', 'FHD', 'UHD', 'SDR', 'HDR']:
                quality_tags[tag] += 1

print(f"\nQuality tag frequency:")
for tag, count in quality_tags.most_common(10):
    print(f"  {tag}: {count}")
