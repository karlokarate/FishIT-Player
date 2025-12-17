#!/bin/bash
#
# Scene Name Parser Validation Against Telegram Chat Exports
#
# This script extracts filenames from all telegram chat exports and tests
# the Re2jSceneNameParser against them to validate parsing accuracy.
#

set -euo pipefail

EXPORTS_DIR="legacy/docs/telegram/exports/exports"
TEMP_DIR="/tmp/telegram-parser-test"
FILENAMES_FILE="$TEMP_DIR/filenames.txt"
RESULTS_FILE="$TEMP_DIR/parser_results.txt"
REPORT_FILE="/tmp/scene_parser_telegram_validation_report.txt"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "================================================================================"
echo "SCENE NAME PARSER VALIDATION - TELEGRAM CHAT EXPORTS"
echo "================================================================================"
echo "Export directory: $EXPORTS_DIR"
echo "Parser: Re2jSceneNameParser (core/metadata-normalizer)"
echo ""

# Create temp directory
mkdir -p "$TEMP_DIR"

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is required but not installed${NC}"
    exit 1
fi

echo "Step 1: Extracting filenames from telegram chat exports..."
echo ""

# Extract all filenames from telegram exports
total_files=0
for json_file in "$EXPORTS_DIR"/*.json; do
    if [ ! -f "$json_file" ]; then
        continue
    fi
    
    # Extract filenames from video content
    jq -r '.messages[]? | select(.content.fileName? != null) | .content.fileName' "$json_file" 2>/dev/null >> "$FILENAMES_FILE" || true
    
    # Also extract from caption field as fallback
    jq -r '.messages[]? | select(.caption? != null) | .caption' "$json_file" 2>/dev/null | grep -E '\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v)' >> "$FILENAMES_FILE" || true
    
    total_files=$((total_files + 1))
    
    if [ $((total_files % 50)) -eq 0 ]; then
        echo "  Processed $total_files export files..."
    fi
done

# Remove duplicates and sort
sort -u "$FILENAMES_FILE" -o "$FILENAMES_FILE"

total_filenames=$(wc -l < "$FILENAMES_FILE")

echo "Extracted $total_filenames unique filenames from $total_files telegram exports"
echo ""

if [ "$total_filenames" -eq 0 ]; then
    echo -e "${YELLOW}No filenames found in exports${NC}"
    exit 0
fi

echo "Step 2: Analyzing filename patterns..."
echo ""

# Analyze patterns
german_titles=$(grep -i -E '(der|die|das|ein|eine) ' "$FILENAMES_FILE" | wc -l)
year_in_name=$(grep -E '[- ][12][09][0-9]{2}[- \.]' "$FILENAMES_FILE" | wc -l)
quality_tags=$(grep -i -E '(1080p|720p|2160p|4k|bluray|web-dl|webrip|hdtv)' "$FILENAMES_FILE" | wc -l)
scene_style=$(grep -E '\.[12][09][0-9]{2}\.' "$FILENAMES_FILE" | wc -l)
hyphenated=$(grep -E ' - .* - [12][09][0-9]{2}' "$FILENAMES_FILE" | wc -l)

echo "Pattern Analysis:"
echo "  German titles (der/die/das):  $german_titles"
echo "  Years detected:                $year_in_name"
echo "  Quality tags:                  $quality_tags"
echo "  Scene-style naming:            $scene_style"
echo "  Hyphenated format:             $hyphenated"
echo ""

echo "Step 3: Sample filenames (first 20):"
echo "--------------------------------------------------------------------------------"
head -20 "$FILENAMES_FILE" | nl
echo "--------------------------------------------------------------------------------"
echo ""

echo "Step 4: Recommendations for parser testing:"
echo ""
echo "To test the Re2jSceneNameParser against these filenames, create a Kotlin test:"
echo ""
echo "1. Add test in: core/metadata-normalizer/src/test/java/.../parser/TelegramExportParserTest.kt"
echo "2. Load filenames from: $FILENAMES_FILE"
echo "3. Parse each with: Re2jSceneNameParser().parse(filename)"
echo "4. Validate extracted: title, year, quality"
echo ""
echo "Expected parser behavior for common patterns:"
echo ""
echo "Pattern: 'Das Ende der Welt - Die 12 Prophezeiungen der Maya - 2012.mp4'"
echo "  → title: 'Das Ende der Welt - Die 12 Prophezeiungen der Maya'"
echo "  → year: 2012"
echo ""
echo "Pattern: 'Terrordactyl - Die Killer Saurier - 2016.mp4'"
echo "  → title: 'Terrordactyl - Die Killer Saurier'"
echo "  → year: 2016"
echo ""
echo "Pattern: 'Movie.Title.2020.1080p.BluRay.x264-GROUP.mkv'"
echo "  → title: 'Movie Title'"
echo "  → year: 2020"
echo "  → quality.resolution: '1080p'"
echo "  → quality.source: 'BluRay'"
echo ""

# Generate detailed report
{
    echo "================================================================================"
    echo "SCENE NAME PARSER - TELEGRAM EXPORT VALIDATION REPORT"
    echo "================================================================================"
    echo "Generated: $(date)"
    echo ""
    
    echo "📊 SUMMARY"
    echo "--------------------------------------------------------------------------------"
    echo "Total telegram exports processed:  $total_files"
    echo "Unique filenames extracted:        $total_filenames"
    echo ""
    
    echo "📋 FILENAME PATTERN DISTRIBUTION"
    echo "--------------------------------------------------------------------------------"
    echo "German titles (der/die/das):       $german_titles ($((german_titles * 100 / total_filenames))%)"
    echo "Years in filename:                 $year_in_name ($((year_in_name * 100 / total_filenames))%)"
    echo "Quality tags present:              $quality_tags ($((quality_tags * 100 / total_filenames))%)"
    echo "Scene-style naming:                $scene_style ($((scene_style * 100 / total_filenames))%)"
    echo "Hyphenated format:                 $hyphenated ($((hyphenated * 100 / total_filenames))%)"
    echo ""
    
    echo "🎬 NAMING CONVENTIONS DETECTED"
    echo "--------------------------------------------------------------------------------"
    echo ""
    echo "1. Hyphenated German Format (Most Common)"
    echo "   Pattern: 'Title - Subtitle - Year.ext'"
    echo "   Examples:"
    head -5 "$FILENAMES_FILE" | sed 's/^/     /'
    echo ""
    
    echo "2. Scene-Style Format"
    if [ "$scene_style" -gt 0 ]; then
        echo "   Pattern: 'Title.Year.Quality.Codec-GROUP.ext'"
        echo "   Examples:"
        grep -E '\.[12][09][0-9]{2}\.' "$FILENAMES_FILE" | head -3 | sed 's/^/     /'
    else
        echo "   (None detected in telegram exports)"
    fi
    echo ""
    
    echo "3. Quality-Tagged Format"
    if [ "$quality_tags" -gt 0 ]; then
        echo "   Pattern: 'Title Year Quality.ext'"
        echo "   Examples:"
        grep -i -E '(1080p|720p|bluray)' "$FILENAMES_FILE" | head -3 | sed 's/^/     /'
    else
        echo "   (None detected in telegram exports)"
    fi
    echo ""
    
    echo "📝 PARSER TEST RECOMMENDATIONS"
    echo "--------------------------------------------------------------------------------"
    echo ""
    echo "The Re2jSceneNameParser should be tested against these telegram filenames"
    echo "to validate its ability to handle:"
    echo ""
    echo "1. German-language titles with articles (der, die, das)"
    echo "2. Hyphenated multi-part titles"
    echo "3. Years at the end of filenames (not embedded in scene format)"
    echo "4. Special characters (ä, ö, ü, ß, -)"
    echo "5. Mixed formats (some scene-style, mostly readable names)"
    echo ""
    echo "Expected Challenges:"
    echo "  • Hyphen preservation in titles (e.g., 'Title - Subtitle' should not lose hyphens)"
    echo "  • German article handling (should be preserved in title)"
    echo "  • Year extraction from hyphenated format (YYYY at end)"
    echo "  • Subtitle detection and handling"
    echo ""
    
    echo "🔬 SAMPLE TEST CASES (Top 30 Filenames)"
    echo "--------------------------------------------------------------------------------"
    head -30 "$FILENAMES_FILE" | nl
    echo ""
    
    echo "================================================================================"
    echo "NEXT STEPS"
    echo "================================================================================"
    echo ""
    echo "1. Create Kotlin unit test: TelegramExportParserTest.kt"
    echo "2. Load filenames from: $FILENAMES_FILE"
    echo "3. Run parser on each filename"
    echo "4. Validate:"
    echo "   • Title extraction (should preserve hyphens and articles)"
    echo "   • Year detection (especially hyphenated format)"
    echo "   • Quality metadata (if present)"
    echo "5. Report parsing success rate and edge cases"
    echo ""
    echo "Full filename list saved to: $FILENAMES_FILE"
    echo ""
    echo "================================================================================"
    
} | tee "$REPORT_FILE"

echo ""
echo -e "${BLUE}Report saved to: $REPORT_FILE${NC}"
echo -e "${BLUE}Filenames saved to: $FILENAMES_FILE${NC}"
echo ""
echo -e "${GREEN}Analysis complete. Ready for parser testing.${NC}"

exit 0
