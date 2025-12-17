#!/usr/bin/env kotlin
@file:DependsOn("/workspaces/FishIT-Player/core/metadata-normalizer/build/libs/*.jar")

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

// Test input strings (simulating real Xtream VOD names)
val testNames = listOf(
    // Pipe-separated (21%)
    "Sisu: Road to Revenge | 2025 | 7.4",
    "Das Geheimnis des Einhorns | 2025 | 5.0",
    "John Wick: Kapitel 4 | 2023 | 4K |",
    "Silent Night, Deadly Night | 2025 | 5.3 | LOWQ",
    
    // Parentheses (56%)
    "Asterix & Obelix im Reich der Mitte (2023)",
    "Evil Dead Rise (2023)",
    "Your Place or Mine (2023)",
    
    // Scene-style (0.5%)
    "The.Ghosts.of.Monday.2022.German.1080p.WEB.H264-LDJD",
    "Amundsen.Wettlauf.zum.Suedpol.2019.German.AC3.DL.1080p.BluRay.x265-HQX",
    
    // No year (22%)
    "Zombies 3",
    "Run.&.Gun"
)

println("=== Parser Test Results ===")
testNames.forEach { name ->
    println("Input: '$name'")
    // We can't directly use the parser here, so let's just print
    println()
}
