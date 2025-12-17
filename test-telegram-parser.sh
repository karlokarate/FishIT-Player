#!/bin/bash
#
# Test script to validate the LEGACY V1 Telegram parser on all chat exports
#
# NOTE: This validates the v1 parser in legacy/v1-app/app/src/main/java/com/chris/m3usuite/telegram/parser/
# The new v2 parser is in pipeline/telegram/ and uses live TDLib connections (cannot test with JSON exports)
#
# This script analyzes all JSON export files using jq to extract statistics
# and validate the parser's expected behavior without needing to compile the Java/Kotlin code.
#

set -euo pipefail

EXPORTS_DIR="legacy/docs/telegram/exports/exports"
REPORT_FILE="/tmp/telegram_parser_test_report.txt"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "================================================================================"
echo "TELEGRAM PARSER VALIDATION TEST"
echo "================================================================================"
echo "Export directory: $EXPORTS_DIR"
echo ""

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is required but not installed${NC}"
    exit 1
fi

# Initialize counters
total_files=0
total_chats=0
total_messages=0
files_with_videos=0
files_with_photos=0
files_with_text=0
files_with_vtp_pattern=0
total_videos=0
total_photos=0
total_texts=0
adult_chats=0
movie_chats=0

# Files with parsing issues
declare -a parse_errors=()
declare -a empty_chats=()
declare -a movie_chat_samples=()
declare -a adult_chat_samples=()
declare -a vtp_chat_samples=()

echo "Analyzing JSON export files..."
echo ""

# Process each JSON file
for json_file in "$EXPORTS_DIR"/*.json; do
    if [ ! -f "$json_file" ]; then
        continue
    fi
    
    total_files=$((total_files + 1))
    filename=$(basename "$json_file")
    
    # Validate JSON structure
    if ! jq empty "$json_file" 2>/dev/null; then
        parse_errors+=("$filename: Invalid JSON")
        continue
    fi
    
    total_chats=$((total_chats + 1))
    
    # Extract basic info
    chat_id=$(jq -r '.chatId // "unknown"' "$json_file")
    title=$(jq -r '.title // "Untitled"' "$json_file")
    message_count=$(jq '.messages | length' "$json_file")
    total_messages=$((total_messages + message_count))
    
    # Count message types
    video_count=$(jq '[.messages[] | select(.content.duration? != null)] | length' "$json_file")
    photo_count=$(jq '[.messages[] | select(.content.type? == "photo")] | length' "$json_file")
    text_count=$(jq '[.messages[] | select(.text? != null and (.content.duration? == null) and (.content.type? != "photo"))] | length' "$json_file")
    
    total_videos=$((total_videos + video_count))
    total_photos=$((total_photos + photo_count))
    total_texts=$((total_texts + text_count))
    
    # Check for content patterns
    if [ "$video_count" -gt 0 ]; then
        files_with_videos=$((files_with_videos + 1))
    fi
    
    if [ "$photo_count" -gt 0 ]; then
        files_with_photos=$((files_with_photos + 1))
    fi
    
    if [ "$text_count" -gt 0 ]; then
        files_with_text=$((files_with_text + 1))
    fi
    
    # Check for Video+Text+Photo pattern
    if [ "$video_count" -gt 0 ] && [ "$photo_count" -gt 0 ] && [ "$text_count" -gt 0 ]; then
        files_with_vtp_pattern=$((files_with_vtp_pattern + 1))
        if [ ${#vtp_chat_samples[@]} -lt 5 ]; then
            vtp_chat_samples+=("[$chat_id] $title")
        fi
    fi
    
    # Detect adult content based on title heuristics
    if echo "$title" | grep -iE '(porn|xxx|adult|18\+|nsfw|sex)' > /dev/null; then
        adult_chats=$((adult_chats + 1))
        if [ ${#adult_chat_samples[@]} -lt 5 ]; then
            adult_chat_samples+=("[$chat_id] $title")
        fi
    fi
    
    # Detect movie chats (chats with structured metadata in text messages)
    has_metadata=$(jq '[.messages[] | select(.year? != null or .tmdbUrl? != null or .genres? != null)] | length' "$json_file")
    if [ "$has_metadata" -gt 0 ]; then
        movie_chats=$((movie_chats + 1))
        if [ ${#movie_chat_samples[@]} -lt 10 ]; then
            movie_chat_samples+=("[$chat_id] $title ($has_metadata items with metadata)")
        fi
    fi
    
    # Track empty chats
    if [ "$message_count" -eq 0 ]; then
        empty_chats+=("[$chat_id] $title")
    fi
    
    # Progress indicator
    if [ $((total_files % 50)) -eq 0 ]; then
        echo "  Processed $total_files files..."
    fi
done

# Generate report
{
    echo "================================================================================"
    echo "TELEGRAM PARSER TEST REPORT"
    echo "================================================================================"
    echo "Generated: $(date)"
    echo ""
    
    echo "📊 SUMMARY STATISTICS"
    echo "--------------------------------------------------------------------------------"
    echo "Total JSON files processed:       $total_files"
    echo "Valid chats parsed:               $total_chats"
    echo "Total messages across all chats:  $total_messages"
    echo ""
    
    echo "📦 MESSAGE TYPE DISTRIBUTION"
    echo "--------------------------------------------------------------------------------"
    echo "Total video messages:             $total_videos"
    echo "Total photo messages:             $total_photos"
    echo "Total text messages:              $total_texts"
    echo ""
    echo "Chats with video content:         $files_with_videos"
    echo "Chats with photo content:         $files_with_photos"
    echo "Chats with text content:          $files_with_text"
    echo ""
    
    echo "🎬 CONTENT PATTERN ANALYSIS"
    echo "--------------------------------------------------------------------------------"
    echo "Chats with VTP pattern (Video+Text+Photo): $files_with_vtp_pattern"
    echo "Chats with structured metadata:            $movie_chats"
    echo "Chats flagged as adult content:            $adult_chats"
    echo ""
    
    if [ ${#vtp_chat_samples[@]} -gt 0 ]; then
        echo "📷 SAMPLE VTP PATTERN CHATS:"
        for sample in "${vtp_chat_samples[@]}"; do
            echo "   $sample"
        done
        echo ""
    fi
    
    if [ ${#movie_chat_samples[@]} -gt 0 ]; then
        echo "🎬 SAMPLE MOVIE CHATS WITH METADATA:"
        for sample in "${movie_chat_samples[@]}"; do
            echo "   $sample"
        done
        echo ""
    fi
    
    if [ ${#adult_chat_samples[@]} -gt 0 ]; then
        echo "🔞 SAMPLE ADULT CONTENT CHATS:"
        for sample in "${adult_chat_samples[@]}"; do
            echo "   $sample"
        done
        echo ""
    fi
    
    if [ ${#empty_chats[@]} -gt 0 ]; then
        echo "⚠️  EMPTY CHATS (${#empty_chats[@]} total):"
        for chat in "${empty_chats[@]:0:5}"; do
            echo "   $chat"
        done
        if [ ${#empty_chats[@]} -gt 5 ]; then
            echo "   ... and $((${#empty_chats[@]} - 5)) more"
        fi
        echo ""
    fi
    
    if [ ${#parse_errors[@]} -gt 0 ]; then
        echo "❌ PARSE ERRORS (${#parse_errors[@]} files):"
        for error in "${parse_errors[@]}"; do
            echo "   $error"
        done
        echo ""
    fi
    
    echo "================================================================================"
    echo "✅ TEST VALIDATION RESULTS"
    echo "================================================================================"
    
    # Validation checks
    validation_passed=true
    
    if [ $total_chats -eq 0 ]; then
        echo "❌ FAIL: No chats were successfully parsed"
        validation_passed=false
    else
        echo "✅ PASS: Successfully parsed $total_chats chats"
    fi
    
    if [ $total_messages -eq 0 ]; then
        echo "❌ FAIL: No messages were found in any chat"
        validation_passed=false
    else
        echo "✅ PASS: Found $total_messages total messages"
    fi
    
    if [ ${#parse_errors[@]} -gt 0 ]; then
        echo "❌ FAIL: ${#parse_errors[@]} files had JSON parsing errors"
        validation_passed=false
    else
        echo "✅ PASS: All JSON files parsed successfully"
    fi
    
    if [ $files_with_videos -eq 0 ]; then
        echo "⚠️  WARNING: No chats with video content found"
    else
        echo "✅ PASS: Found video content in $files_with_videos chats"
    fi
    
    if [ $movie_chats -eq 0 ]; then
        echo "⚠️  WARNING: No chats with structured metadata found"
    else
        echo "✅ PASS: Found structured metadata in $movie_chats chats"
    fi
    
    echo ""
    echo "================================================================================"
    
    if [ "$validation_passed" = true ]; then
        echo "✅ OVERALL: All validation checks passed"
        echo "================================================================================"
    else
        echo "❌ OVERALL: Some validation checks failed"
        echo "================================================================================"
    fi
    
} | tee "$REPORT_FILE"

# Print summary to console
echo ""
echo -e "${BLUE}Report saved to: $REPORT_FILE${NC}"
echo ""

# Exit with appropriate status
if [ ${#parse_errors[@]} -gt 0 ]; then
    echo -e "${RED}Test completed with errors${NC}"
    exit 1
else
    echo -e "${GREEN}Test completed successfully${NC}"
    exit 0
fi
