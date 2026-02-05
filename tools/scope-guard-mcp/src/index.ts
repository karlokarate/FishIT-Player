#!/usr/bin/env node
/**
 * Scope Guard MCP Server v3.0
 * 
 * Features:
 * - Schema & guidance delivery in every response
 * - Agent modes: interactive (user confirmation) vs unattended (auto-assign with summary)
 * - Bundle support for predefined file groups
 * - globalExcludes for build artifacts
 * - readOnlyPaths for legacy protection
 * - Audit trail with persistent logging
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
    CallToolRequestSchema,
    ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import * as fs from "fs";
import * as path from "path";

// ============================================================================
// Types
// ============================================================================

interface ScopeConfig {
    maxFilesPerScope: number;
    maxLOCPerScope: number;
    freshnessWarningDays: number;
    requireUserConfirmation: boolean;
    auditLogPath: string;
    globalExcludes: string[];
    readOnlyPaths: string[];
    bundles: Record<string, BundleConfig>;
    scopeFileSchema: Record<string, unknown>;
}

interface BundleConfig {
    description: string;
    patterns: string[];
    invariants: string[];
    mandatoryReadBeforeEdit?: string[];
    ssot?: string;
    generated?: string[];
}

interface ScopeFile {
    scopeId: string;
    version: string;
    description?: string;
    modules: Record<string, ModuleConfig>;
    mandatoryReadBeforeEdit?: string[];
    globalInvariants?: string[];
    forbiddenPatterns?: Array<{ pattern: string; reason: string }>;
    relatedScopes?: string[];
    lastVerified?: string;
}

interface ModuleConfig {
    fileCount?: number;
    totalLOC?: number;
    criticalFiles?: CriticalFile[];
    diModules?: string[];
    handlers?: string[];
}

interface CriticalFile {
    path: string;
    loc?: number;
    purpose?: string;
    invariants?: string[];
    knownBugs?: string[];
}

interface AuditEntry {
    timestamp: string;
    action: string;
    filePath?: string;
    scopeId?: string;
    status: string;
    agentMode?: string;
    details?: string;
}

interface PendingAssignment {
    filePath: string;
    scopeId: string;
    suggestedScope?: string;
    justification?: string;
    timestamp: string;
    agentMode: string;
}

interface SessionSummary {
    sessionId: string;
    agentMode: string;
    startTime: string;
    scopesRead: string[];
    filesChecked: string[];
    filesEdited: string[];
    autoAssignments: Array<{ file: string; scope: string }>;
    bundlesUsed: string[];
}

// ============================================================================
// Global State
// ============================================================================

const WORKSPACE_ROOT = process.env.WORKSPACE_ROOT || process.cwd();
const SCOPE_DIR = path.join(WORKSPACE_ROOT, ".scope");

let config: ScopeConfig;
let scopes: Map<string, ScopeFile> = new Map();
let scopesRead: Set<string> = new Set();
let pendingAssignments: Map<string, PendingAssignment> = new Map();
let currentSession: SessionSummary | null = null;

// ============================================================================
// Logging
// ============================================================================

function log(message: string): void {
    console.error(`[ScopeGuard ${new Date().toISOString()}] ${message}`);
}

function audit(entry: Omit<AuditEntry, "timestamp">): void {
    const fullEntry: AuditEntry = {
        ...entry,
        timestamp: new Date().toISOString(),
    };

    const logPath = path.join(WORKSPACE_ROOT, config?.auditLogPath || ".scope/audit.log");
    const logLine = JSON.stringify(fullEntry) + "\n";

    try {
        fs.appendFileSync(logPath, logLine);
    } catch (e) {
        log(`Failed to write audit log: ${e}`);
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

function loadConfig(): ScopeConfig {
    const configPath = path.join(SCOPE_DIR, "scope-guard.config.json");
    try {
        const content = fs.readFileSync(configPath, "utf-8");
        return JSON.parse(content);
    } catch (e) {
        log(`Config not found, using defaults: ${e}`);
        return {
            maxFilesPerScope: 50,
            maxLOCPerScope: 15000,
            freshnessWarningDays: 30,
            requireUserConfirmation: true,
            auditLogPath: ".scope/audit.log",
            globalExcludes: ["**/build/**", "**/.gradle/**", "**/node_modules/**"],
            readOnlyPaths: ["legacy/**", "app/**"],
            bundles: {},
            scopeFileSchema: {},
        };
    }
}

function loadScopes(): void {
    scopes.clear();

    if (!fs.existsSync(SCOPE_DIR)) {
        log("No .scope directory found");
        return;
    }

    const files = fs.readdirSync(SCOPE_DIR);
    for (const file of files) {
        if (file.endsWith(".scope.json")) {
            try {
                const content = fs.readFileSync(path.join(SCOPE_DIR, file), "utf-8");
                const scope: ScopeFile = JSON.parse(content);
                scopes.set(scope.scopeId, scope);
                log(`Loaded scope: ${scope.scopeId} (${Object.keys(scope.modules).length} modules)`);
            } catch (e) {
                log(`Failed to load scope ${file}: ${e}`);
            }
        }
    }
}

function matchesGlob(filePath: string, pattern: string): boolean {
    // Simple glob matching: ** = any path, * = any name segment
    const regexPattern = pattern
        .replace(/\*\*/g, "<<<DOUBLESTAR>>>")
        .replace(/\*/g, "[^/]*")
        .replace(/<<<DOUBLESTAR>>>/g, ".*")
        .replace(/\?/g, ".");

    const regex = new RegExp(`^${regexPattern}$`);
    return regex.test(filePath);
}

function isExcluded(filePath: string): boolean {
    const relativePath = filePath.startsWith(WORKSPACE_ROOT)
        ? filePath.slice(WORKSPACE_ROOT.length + 1)
        : filePath;

    for (const pattern of config.globalExcludes || []) {
        if (matchesGlob(relativePath, pattern)) {
            return true;
        }
    }
    return false;
}

function isReadOnly(filePath: string): { readOnly: boolean; reason?: string } {
    const relativePath = filePath.startsWith(WORKSPACE_ROOT)
        ? filePath.slice(WORKSPACE_ROOT.length + 1)
        : filePath;

    for (const pattern of config.readOnlyPaths || []) {
        if (matchesGlob(relativePath, pattern)) {
            // Check if it's a generated file in a bundle
            for (const [bundleId, bundle] of Object.entries(config.bundles || {})) {
                if (bundle.generated?.some(g => matchesGlob(relativePath, g))) {
                    return {
                        readOnly: true,
                        reason: `GENERATED file in bundle '${bundleId}'. Edit source: ${bundle.ssot || "see bundle config"}`,
                    };
                }
            }
            return {
                readOnly: true,
                reason: `Path matches readOnlyPaths pattern: ${pattern}`,
            };
        }
    }
    return { readOnly: false };
}

function findMatchingBundle(filePath: string): { bundleId: string; bundle: BundleConfig } | null {
    const relativePath = filePath.startsWith(WORKSPACE_ROOT)
        ? filePath.slice(WORKSPACE_ROOT.length + 1)
        : filePath;

    for (const [bundleId, bundle] of Object.entries(config.bundles || {})) {
        for (const pattern of bundle.patterns) {
            if (matchesGlob(relativePath, pattern)) {
                return { bundleId, bundle };
            }
        }
    }
    return null;
}

function findScopeForFile(filePath: string): { scopeId: string; scope: ScopeFile } | null {
    const relativePath = filePath.startsWith(WORKSPACE_ROOT)
        ? filePath.slice(WORKSPACE_ROOT.length + 1)
        : filePath;

    for (const [scopeId, scope] of scopes) {
        for (const modulePath of Object.keys(scope.modules)) {
            if (relativePath.startsWith(modulePath) || relativePath.startsWith(modulePath + "/")) {
                return { scopeId, scope };
            }
        }
    }
    return null;
}

function suggestScopeForFile(filePath: string): string | null {
    const relativePath = filePath.startsWith(WORKSPACE_ROOT)
        ? filePath.slice(WORKSPACE_ROOT.length + 1)
        : filePath;

    // Find best matching scope by path similarity
    let bestMatch: { scopeId: string; matchLength: number } | null = null;

    for (const [scopeId, scope] of scopes) {
        for (const modulePath of Object.keys(scope.modules)) {
            // Check if file path starts with similar directory structure
            const parts = relativePath.split("/");
            const moduleParts = modulePath.split("/");

            let matchLength = 0;
            for (let i = 0; i < Math.min(parts.length, moduleParts.length); i++) {
                if (parts[i] === moduleParts[i]) {
                    matchLength++;
                } else {
                    break;
                }
            }

            if (matchLength > 0 && (!bestMatch || matchLength > bestMatch.matchLength)) {
                bestMatch = { scopeId, matchLength };
            }
        }
    }

    return bestMatch?.scopeId || null;
}

function getUnreadRelatedScopes(scopeId: string): string[] {
    const scope = scopes.get(scopeId);
    if (!scope?.relatedScopes) return [];

    return scope.relatedScopes.filter(id => !scopesRead.has(id));
}

function checkScopeFreshness(scope: ScopeFile): { fresh: boolean; daysSinceVerified?: number } {
    if (!scope.lastVerified) {
        return { fresh: false };
    }

    const verified = new Date(scope.lastVerified);
    const now = new Date();
    const daysSince = Math.floor((now.getTime() - verified.getTime()) / (1000 * 60 * 60 * 24));

    return {
        fresh: daysSince <= config.freshnessWarningDays,
        daysSinceVerified: daysSince,
    };
}

// ============================================================================
// Response Helpers - Schema & Guidance in Every Response
// ============================================================================

interface GuidanceBlock {
    status: string;
    agentMode: string;
    nextSteps: string[];
    warnings: string[];
    scopeSchema?: Record<string, unknown>;
}

function createGuidance(
    status: string,
    agentMode: string,
    nextSteps: string[],
    warnings: string[] = [],
    includeSchema: boolean = false
): GuidanceBlock {
    const guidance: GuidanceBlock = {
        status,
        agentMode,
        nextSteps,
        warnings,
    };

    if (includeSchema) {
        guidance.scopeSchema = config.scopeFileSchema;
    }

    return guidance;
}

function formatResponse(
    data: Record<string, unknown>,
    guidance: GuidanceBlock
): string {
    return JSON.stringify({
        _scopeGuard: {
            version: "3.0",
            timestamp: new Date().toISOString(),
            guidance,
        },
        ...data,
    }, null, 2);
}

// ============================================================================
// Session Management (for unattended agents)
// ============================================================================

function startSession(agentMode: string): SessionSummary {
    currentSession = {
        sessionId: `session-${Date.now()}`,
        agentMode,
        startTime: new Date().toISOString(),
        scopesRead: [],
        filesChecked: [],
        filesEdited: [],
        autoAssignments: [],
        bundlesUsed: [],
    };
    return currentSession;
}

function trackSessionAction(
    action: "scopeRead" | "fileChecked" | "fileEdited" | "autoAssign" | "bundleUsed",
    value: string | { file: string; scope: string }
): void {
    if (!currentSession) {
        currentSession = startSession("unknown");
    }

    switch (action) {
        case "scopeRead":
            if (!currentSession.scopesRead.includes(value as string)) {
                currentSession.scopesRead.push(value as string);
            }
            break;
        case "fileChecked":
            if (!currentSession.filesChecked.includes(value as string)) {
                currentSession.filesChecked.push(value as string);
            }
            break;
        case "fileEdited":
            if (!currentSession.filesEdited.includes(value as string)) {
                currentSession.filesEdited.push(value as string);
            }
            break;
        case "autoAssign":
            currentSession.autoAssignments.push(value as { file: string; scope: string });
            break;
        case "bundleUsed":
            if (!currentSession.bundlesUsed.includes(value as string)) {
                currentSession.bundlesUsed.push(value as string);
            }
            break;
    }
}

// ============================================================================
// Tool Implementations
// ============================================================================

function handleScopeGuardCheck(args: { file_path: string; agent_mode?: string }): string {
    const { file_path, agent_mode = "interactive" } = args;
    const relativePath = file_path.startsWith(WORKSPACE_ROOT)
        ? file_path.slice(WORKSPACE_ROOT.length + 1)
        : file_path;

    trackSessionAction("fileChecked", relativePath);

    // 1. Check if excluded (build artifacts, etc.)
    if (isExcluded(file_path)) {
        audit({ action: "check", filePath: relativePath, status: "EXCLUDED", agentMode: agent_mode });
        return formatResponse(
            {
                status: "EXCLUDED",
                file_path: relativePath,
                reason: "File matches globalExcludes pattern - no scope check needed",
            },
            createGuidance("EXCLUDED", agent_mode, ["Proceed with edit - file is excluded from scope system"])
        );
    }

    // 2. Check if read-only
    const readOnlyCheck = isReadOnly(file_path);
    if (readOnlyCheck.readOnly) {
        audit({ action: "check", filePath: relativePath, status: "READ_ONLY", agentMode: agent_mode });
        return formatResponse(
            {
                status: "READ_ONLY",
                file_path: relativePath,
                reason: readOnlyCheck.reason,
            },
            createGuidance("READ_ONLY", agent_mode, [
                "DO NOT edit this file",
                readOnlyCheck.reason?.includes("GENERATED")
                    ? "Edit the source file instead (see reason)"
                    : "This path is protected by architecture rules",
            ], ["Editing this file violates architecture rules"])
        );
    }

    // 3. Check if matches a bundle
    const bundleMatch = findMatchingBundle(file_path);
    if (bundleMatch) {
        const { bundleId, bundle } = bundleMatch;
        const bundleRead = scopesRead.has(`bundle:${bundleId}`);
        trackSessionAction("bundleUsed", bundleId);

        if (!bundleRead) {
            audit({ action: "check", filePath: relativePath, status: "BUNDLE_BLOCKED", agentMode: agent_mode, details: bundleId });
            return formatResponse(
                {
                    status: "BUNDLE_BLOCKED",
                    file_path: relativePath,
                    bundle_id: bundleId,
                    bundle_description: bundle.description,
                    invariants: bundle.invariants,
                    mandatory_read: bundle.mandatoryReadBeforeEdit || [],
                },
                createGuidance("BUNDLE_BLOCKED", agent_mode, [
                    `Call scope_guard_read_bundle with bundle_id="${bundleId}"`,
                    "Review the bundle's invariants",
                    "Read mandatory files: " + (bundle.mandatoryReadBeforeEdit?.join(", ") || "none"),
                    "Then call scope_guard_check again",
                ], [], true)
            );
        }

        audit({ action: "check", filePath: relativePath, status: "BUNDLE_ALLOWED", agentMode: agent_mode, details: bundleId });
        return formatResponse(
            {
                status: "ALLOWED",
                file_path: relativePath,
                via: "bundle",
                bundle_id: bundleId,
                invariants: bundle.invariants,
            },
            createGuidance("ALLOWED", agent_mode, [
                "Proceed with edit",
                "Follow bundle invariants: " + bundle.invariants.join("; "),
            ])
        );
    }

    // 4. Check if in a scope
    const scopeMatch = findScopeForFile(file_path);
    if (scopeMatch) {
        const { scopeId, scope } = scopeMatch;

        // Check related scopes
        const unreadRelated = getUnreadRelatedScopes(scopeId);

        if (!scopesRead.has(scopeId)) {
            audit({ action: "check", filePath: relativePath, status: "BLOCKED", agentMode: agent_mode, scopeId });
            return formatResponse(
                {
                    status: "BLOCKED",
                    file_path: relativePath,
                    scope_id: scopeId,
                    scope_description: scope.description,
                    global_invariants: scope.globalInvariants,
                    mandatory_read: scope.mandatoryReadBeforeEdit,
                    related_scopes: scope.relatedScopes,
                    unread_related: unreadRelated,
                },
                createGuidance("BLOCKED", agent_mode, [
                    `Call scope_guard_get with scope_id="${scopeId}"`,
                    "Review globalInvariants and forbiddenPatterns",
                    `Call scope_guard_read with scope_id="${scopeId}"`,
                    unreadRelated.length > 0 ? `Also read related scopes: ${unreadRelated.join(", ")}` : "",
                    "Then call scope_guard_check again",
                ].filter(Boolean), [], true)
            );
        }

        // Check related scopes are read
        if (unreadRelated.length > 0) {
            audit({ action: "check", filePath: relativePath, status: "RELATED_UNREAD", agentMode: agent_mode, scopeId });
            return formatResponse(
                {
                    status: "RELATED_UNREAD",
                    file_path: relativePath,
                    scope_id: scopeId,
                    unread_related_scopes: unreadRelated,
                },
                createGuidance("RELATED_UNREAD", agent_mode, [
                    `Read related scopes first: ${unreadRelated.join(", ")}`,
                    "Use scope_guard_get and scope_guard_read for each",
                    "Then call scope_guard_check again",
                ])
            );
        }

        // Check freshness
        const freshness = checkScopeFreshness(scope);
        const warnings: string[] = [];
        if (!freshness.fresh) {
            warnings.push(`Scope not verified for ${freshness.daysSinceVerified || "unknown"} days - may be outdated`);
        }

        audit({ action: "check", filePath: relativePath, status: "ALLOWED", agentMode: agent_mode, scopeId });
        return formatResponse(
            {
                status: "ALLOWED",
                file_path: relativePath,
                scope_id: scopeId,
                global_invariants: scope.globalInvariants,
                forbidden_patterns: scope.forbiddenPatterns,
                freshness_warning: !freshness.fresh ? `Last verified ${freshness.daysSinceVerified} days ago` : null,
            },
            createGuidance("ALLOWED", agent_mode, [
                "Proceed with edit",
                "Follow globalInvariants",
                "Avoid forbiddenPatterns",
            ], warnings)
        );
    }

    // 5. File is UNTRACKED
    const suggestedScope = suggestScopeForFile(file_path);

    if (agent_mode === "unattended") {
        // Unattended agents auto-assign to suggested scope
        if (suggestedScope) {
            trackSessionAction("autoAssign", { file: relativePath, scope: suggestedScope });
            audit({ action: "auto_assign", filePath: relativePath, status: "AUTO_ASSIGNED", agentMode: agent_mode, scopeId: suggestedScope });
            return formatResponse(
                {
                    status: "AUTO_ASSIGNED",
                    file_path: relativePath,
                    assigned_scope: suggestedScope,
                    note: "UNATTENDED MODE: Auto-assigned to suggested scope. MUST report in session summary.",
                },
                createGuidance("AUTO_ASSIGNED", agent_mode, [
                    `File auto-assigned to scope '${suggestedScope}'`,
                    "Read the scope with scope_guard_get and scope_guard_read",
                    "MUST call scope_guard_summary at end of session to report this assignment",
                ], ["Auto-assignment must be documented in session summary"])
            );
        } else {
            // No suggested scope - unattended agent must create summary
            audit({ action: "check", filePath: relativePath, status: "UNTRACKED_NO_SUGGESTION", agentMode: agent_mode });
            return formatResponse(
                {
                    status: "UNTRACKED_NO_SUGGESTION",
                    file_path: relativePath,
                    note: "UNATTENDED MODE: No matching scope found. Document in session summary.",
                },
                createGuidance("UNTRACKED_NO_SUGGESTION", agent_mode, [
                    "No existing scope matches this file",
                    "Proceed with caution",
                    "MUST document this in scope_guard_summary at end of session",
                    "Consider creating a new scope file for this area",
                ], ["File is untracked - document in session summary"], true)
            );
        }
    }

    // Interactive mode - require user confirmation
    audit({ action: "check", filePath: relativePath, status: "UNTRACKED", agentMode: agent_mode });
    return formatResponse(
        {
            status: "UNTRACKED",
            file_path: relativePath,
            suggested_scope: suggestedScope,
            available_scopes: Array.from(scopes.keys()),
            available_bundles: Object.keys(config.bundles || {}),
        },
        createGuidance("UNTRACKED", agent_mode, [
            suggestedScope
                ? `Call scope_guard_assign with file_path and scope_id="${suggestedScope}"`
                : "Call scope_guard_assign with file_path and appropriate scope_id",
            "User confirmation will be required",
            "Or use scope_guard_request_new_scope if no existing scope fits",
        ], ["File not covered by any scope - must be assigned before editing"], true)
    );
}

function handleScopeGuardGet(args: { scope_id: string }): string {
    const { scope_id } = args;

    // Check if it's a bundle
    if (config.bundles?.[scope_id]) {
        const bundle = config.bundles[scope_id];
        return formatResponse(
            {
                type: "bundle",
                bundle_id: scope_id,
                description: bundle.description,
                patterns: bundle.patterns,
                invariants: bundle.invariants,
                mandatory_read: bundle.mandatoryReadBeforeEdit,
                ssot: bundle.ssot,
                generated: bundle.generated,
            },
            createGuidance("BUNDLE_INFO", "interactive", [
                "Review the bundle invariants",
                "Read mandatory files if listed",
                `Call scope_guard_read_bundle with bundle_id="${scope_id}"`,
            ])
        );
    }

    const scope = scopes.get(scope_id);
    if (!scope) {
        return formatResponse(
            {
                error: "SCOPE_NOT_FOUND",
                scope_id,
                available_scopes: Array.from(scopes.keys()),
                available_bundles: Object.keys(config.bundles || {}),
            },
            createGuidance("ERROR", "interactive", [
                "Check available scopes and bundles",
                "Use correct scope_id or bundle_id",
            ])
        );
    }

    const freshness = checkScopeFreshness(scope);

    return formatResponse(
        {
            type: "scope",
            scope_id,
            version: scope.version,
            description: scope.description,
            modules: scope.modules,
            mandatory_read: scope.mandatoryReadBeforeEdit,
            global_invariants: scope.globalInvariants,
            forbidden_patterns: scope.forbiddenPatterns,
            related_scopes: scope.relatedScopes,
            last_verified: scope.lastVerified,
            freshness: freshness.fresh ? "current" : `stale (${freshness.daysSinceVerified} days)`,
        },
        createGuidance("SCOPE_INFO", "interactive", [
            "Review globalInvariants carefully",
            "Note forbiddenPatterns to avoid",
            "Read mandatoryReadBeforeEdit files",
            `Call scope_guard_read with scope_id="${scope_id}" to acknowledge`,
            scope.relatedScopes?.length ? `Also review related scopes: ${scope.relatedScopes.join(", ")}` : "",
        ].filter(Boolean), freshness.fresh ? [] : [`Scope not verified for ${freshness.daysSinceVerified} days`])
    );
}

function handleScopeGuardRead(args: { scope_id: string }): string {
    const { scope_id } = args;

    const scope = scopes.get(scope_id);
    if (!scope) {
        return formatResponse(
            { error: "SCOPE_NOT_FOUND", scope_id },
            createGuidance("ERROR", "interactive", ["Use scope_guard_get to find valid scope_id"])
        );
    }

    scopesRead.add(scope_id);
    trackSessionAction("scopeRead", scope_id);
    audit({ action: "read", scopeId: scope_id, status: "ACKNOWLEDGED" });

    const unreadRelated = getUnreadRelatedScopes(scope_id);

    return formatResponse(
        {
            status: "ACKNOWLEDGED",
            scope_id,
            message: `Scope '${scope_id}' marked as read for this session`,
            unread_related_scopes: unreadRelated,
        },
        createGuidance("ACKNOWLEDGED", "interactive", [
            unreadRelated.length > 0
                ? `Read related scopes: ${unreadRelated.join(", ")}`
                : "All related scopes read - can now edit files in this scope",
            "Call scope_guard_check to verify file access",
        ])
    );
}

function handleScopeGuardReadBundle(args: { bundle_id: string }): string {
    const { bundle_id } = args;

    const bundle = config.bundles?.[bundle_id];
    if (!bundle) {
        return formatResponse(
            {
                error: "BUNDLE_NOT_FOUND",
                bundle_id,
                available_bundles: Object.keys(config.bundles || {}),
            },
            createGuidance("ERROR", "interactive", ["Check available bundles"])
        );
    }

    scopesRead.add(`bundle:${bundle_id}`);
    trackSessionAction("bundleUsed", bundle_id);
    trackSessionAction("scopeRead", `bundle:${bundle_id}`);
    audit({ action: "read_bundle", scopeId: bundle_id, status: "ACKNOWLEDGED" });

    return formatResponse(
        {
            status: "ACKNOWLEDGED",
            bundle_id,
            message: `Bundle '${bundle_id}' marked as read for this session`,
            invariants_to_follow: bundle.invariants,
            mandatory_files: bundle.mandatoryReadBeforeEdit,
        },
        createGuidance("ACKNOWLEDGED", "interactive", [
            "Can now edit files matching bundle patterns",
            "Follow invariants: " + bundle.invariants.join("; "),
            bundle.mandatoryReadBeforeEdit?.length
                ? "Read these files first: " + bundle.mandatoryReadBeforeEdit.join(", ")
                : "",
        ].filter(Boolean))
    );
}

function handleScopeGuardList(): string {
    const scopeList = Array.from(scopes.entries()).map(([id, scope]) => ({
        scope_id: id,
        description: scope.description,
        modules: Object.keys(scope.modules),
        is_read: scopesRead.has(id),
        related_scopes: scope.relatedScopes,
    }));

    const bundleList = Object.entries(config.bundles || {}).map(([id, bundle]) => ({
        bundle_id: id,
        description: bundle.description,
        patterns: bundle.patterns,
        is_read: scopesRead.has(`bundle:${id}`),
    }));

    return formatResponse(
        {
            scopes: scopeList,
            bundles: bundleList,
            global_excludes: config.globalExcludes,
            read_only_paths: config.readOnlyPaths,
        },
        createGuidance("LIST", "interactive", [
            "Use scope_guard_check before editing any file",
            "Use scope_guard_get to view scope/bundle details",
        ])
    );
}

function handleScopeGuardAssign(args: {
    file_path: string;
    scope_id: string;
    justification?: string;
    agent_mode?: string;
}): string {
    const { file_path, scope_id, justification, agent_mode = "interactive" } = args;
    const relativePath = file_path.startsWith(WORKSPACE_ROOT)
        ? file_path.slice(WORKSPACE_ROOT.length + 1)
        : file_path;

    // Verify scope exists
    if (!scopes.has(scope_id) && !config.bundles?.[scope_id]) {
        return formatResponse(
            {
                error: "INVALID_SCOPE",
                scope_id,
                available_scopes: Array.from(scopes.keys()),
                available_bundles: Object.keys(config.bundles || {}),
            },
            createGuidance("ERROR", agent_mode, ["Use a valid scope_id or bundle_id"])
        );
    }

    const suggestedScope = suggestScopeForFile(file_path);

    // If not using suggested scope, require justification
    if (suggestedScope && scope_id !== suggestedScope && !justification) {
        return formatResponse(
            {
                status: "JUSTIFICATION_REQUIRED",
                file_path: relativePath,
                requested_scope: scope_id,
                suggested_scope: suggestedScope,
            },
            createGuidance("JUSTIFICATION_REQUIRED", agent_mode, [
                `Suggested scope is '${suggestedScope}' but you requested '${scope_id}'`,
                "Re-call with justification parameter explaining why",
            ])
        );
    }

    if (agent_mode === "unattended") {
        // Unattended: auto-assign without user confirmation
        trackSessionAction("autoAssign", { file: relativePath, scope: scope_id });
        audit({
            action: "assign",
            filePath: relativePath,
            scopeId: scope_id,
            status: "AUTO_ASSIGNED",
            agentMode: agent_mode,
            details: justification,
        });

        return formatResponse(
            {
                status: "AUTO_ASSIGNED",
                file_path: relativePath,
                scope_id,
                note: "UNATTENDED MODE: Assignment recorded. MUST report in scope_guard_summary.",
            },
            createGuidance("AUTO_ASSIGNED", agent_mode, [
                "Assignment recorded in session",
                "Proceed with reading the scope",
                "MUST call scope_guard_summary at end of session",
            ], ["Document this auto-assignment in final summary"])
        );
    }

    // Interactive: require user confirmation
    pendingAssignments.set(relativePath, {
        filePath: relativePath,
        scopeId: scope_id,
        suggestedScope: suggestedScope || undefined,
        justification,
        timestamp: new Date().toISOString(),
        agentMode: agent_mode,
    });

    audit({
        action: "assign_pending",
        filePath: relativePath,
        scopeId: scope_id,
        status: "REQUIRES_USER_CONFIRMATION",
        agentMode: agent_mode,
    });

    return formatResponse(
        {
            status: "REQUIRES_USER_CONFIRMATION",
            file_path: relativePath,
            scope_id,
            suggested_scope: suggestedScope,
            justification,
            message: "INFORM USER: Assignment requires confirmation",
        },
        createGuidance("REQUIRES_USER_CONFIRMATION", agent_mode, [
            "Present this to the user for confirmation",
            `"File '${relativePath}' will be assigned to scope '${scope_id}'. Confirm?"`,
            "Call scope_guard_confirm with user's response",
        ])
    );
}

function handleScopeGuardConfirm(args: { file_path: string; confirmed: boolean }): string {
    const { file_path, confirmed } = args;
    const relativePath = file_path.startsWith(WORKSPACE_ROOT)
        ? file_path.slice(WORKSPACE_ROOT.length + 1)
        : file_path;

    const pending = pendingAssignments.get(relativePath);
    if (!pending) {
        return formatResponse(
            {
                error: "NO_PENDING_ASSIGNMENT",
                file_path: relativePath,
            },
            createGuidance("ERROR", "interactive", [
                "No pending assignment for this file",
                "Use scope_guard_assign first",
            ])
        );
    }

    pendingAssignments.delete(relativePath);

    if (!confirmed) {
        audit({
            action: "confirm",
            filePath: relativePath,
            scopeId: pending.scopeId,
            status: "REJECTED",
        });
        return formatResponse(
            {
                status: "REJECTED",
                file_path: relativePath,
                scope_id: pending.scopeId,
                message: "Assignment rejected by user",
            },
            createGuidance("REJECTED", "interactive", [
                "Assignment was not confirmed",
                "Cannot edit this file without scope assignment",
                "Use scope_guard_assign with different scope_id, or",
                "Use scope_guard_request_new_scope to create a new scope",
            ])
        );
    }

    audit({
        action: "confirm",
        filePath: relativePath,
        scopeId: pending.scopeId,
        status: "CONFIRMED",
    });

    return formatResponse(
        {
            status: "CONFIRMED",
            file_path: relativePath,
            scope_id: pending.scopeId,
            message: "Assignment confirmed. Update the scope file manually.",
            instruction: `Add '${relativePath}' to .scope/${pending.scopeId}.scope.json`,
        },
        createGuidance("CONFIRMED", "interactive", [
            `Manually add file to .scope/${pending.scopeId}.scope.json`,
            "Then restart scope-guard server or reload scopes",
            "Then call scope_guard_check again",
        ])
    );
}

function handleScopeGuardRequestNewScope(args: {
    scope_id: string;
    description: string;
    module_paths: string[];
    invariants?: string[];
}): string {
    const { scope_id, description, module_paths, invariants } = args;

    // Check if scope already exists
    if (scopes.has(scope_id)) {
        return formatResponse(
            {
                error: "SCOPE_EXISTS",
                scope_id,
                message: "Use existing scope instead",
            },
            createGuidance("ERROR", "interactive", [
                `Scope '${scope_id}' already exists`,
                "Use scope_guard_assign to add files to existing scope",
            ])
        );
    }

    // Check if module paths overlap with existing scopes
    const overlaps: { path: string; existingScope: string }[] = [];
    for (const modulePath of module_paths) {
        for (const [existingScopeId, existingScope] of scopes) {
            for (const existingModulePath of Object.keys(existingScope.modules)) {
                if (modulePath.startsWith(existingModulePath) || existingModulePath.startsWith(modulePath)) {
                    overlaps.push({ path: modulePath, existingScope: existingScopeId });
                }
            }
        }
    }

    if (overlaps.length > 0) {
        return formatResponse(
            {
                error: "PATH_OVERLAP",
                scope_id,
                overlaps,
                message: "Cannot create scope - paths overlap with existing scopes",
            },
            createGuidance("ERROR", "interactive", [
                "Requested module paths overlap with existing scopes",
                "Use scope_guard_assign to add files to existing scopes",
                "Or adjust module_paths to not overlap",
            ])
        );
    }

    // Generate scope file template
    const scopeTemplate: ScopeFile = {
        scopeId: scope_id,
        version: "1.0.0",
        description,
        modules: Object.fromEntries(
            module_paths.map(p => [p, { fileCount: 0, totalLOC: 0, criticalFiles: [] }])
        ),
        globalInvariants: invariants || [],
        mandatoryReadBeforeEdit: [],
        forbiddenPatterns: [],
        relatedScopes: [],
        lastVerified: new Date().toISOString().split("T")[0],
    };

    audit({
        action: "request_new_scope",
        scopeId: scope_id,
        status: "TEMPLATE_GENERATED",
        details: JSON.stringify(module_paths),
    });

    return formatResponse(
        {
            status: "TEMPLATE_GENERATED",
            scope_id,
            template: scopeTemplate,
            file_path: `.scope/${scope_id}.scope.json`,
            message: "REQUIRES USER APPROVAL: Create this scope file manually",
        },
        createGuidance("TEMPLATE_GENERATED", "interactive", [
            "Present template to user for approval",
            `If approved, create file: .scope/${scope_id}.scope.json`,
            "Restart scope-guard server after creating file",
        ], [], true)
    );
}

function handleScopeGuardValidateCode(args: { scope_id: string; code: string }): string {
    const { scope_id, code } = args;

    const scope = scopes.get(scope_id);
    if (!scope) {
        return formatResponse(
            { error: "SCOPE_NOT_FOUND", scope_id },
            createGuidance("ERROR", "interactive", ["Use valid scope_id"])
        );
    }

    const violations: Array<{ pattern: string; reason: string; match?: string }> = [];

    for (const { pattern, reason } of scope.forbiddenPatterns || []) {
        try {
            const regex = new RegExp(pattern, "gi");
            const match = code.match(regex);
            if (match) {
                violations.push({ pattern, reason, match: match[0] });
            }
        } catch (e) {
            // Invalid regex, skip
        }
    }

    const status = violations.length > 0 ? "VIOLATIONS_FOUND" : "VALID";

    audit({
        action: "validate_code",
        scopeId: scope_id,
        status,
        details: violations.length > 0 ? JSON.stringify(violations) : undefined,
    });

    return formatResponse(
        {
            status,
            scope_id,
            violations,
            global_invariants: scope.globalInvariants,
        },
        createGuidance(status, "interactive",
            violations.length > 0
                ? ["Fix violations before proceeding", "Revise code to avoid forbidden patterns"]
                : ["Code passes validation", "Proceed with edit"]
            , violations.map(v => `Violation: ${v.pattern} - ${v.reason}`))
    );
}

function handleScopeGuardAuditLog(args: { limit?: number }): string {
    const { limit = 20 } = args;
    const logPath = path.join(WORKSPACE_ROOT, config.auditLogPath);

    try {
        const content = fs.readFileSync(logPath, "utf-8");
        const lines = content.trim().split("\n").slice(-limit);
        const entries = lines.map(line => {
            try {
                return JSON.parse(line);
            } catch {
                return { raw: line };
            }
        });

        return formatResponse(
            {
                entries,
                total_shown: entries.length,
            },
            createGuidance("AUDIT_LOG", "interactive", ["Review recent scope guard activity"])
        );
    } catch (e) {
        return formatResponse(
            {
                error: "LOG_READ_ERROR",
                message: String(e),
            },
            createGuidance("ERROR", "interactive", ["Audit log may not exist yet"])
        );
    }
}

function handleScopeGuardSummary(args: {
    edited_files?: string[];
    notes?: string;
}): string {
    const { edited_files, notes } = args;

    if (!currentSession) {
        return formatResponse(
            {
                error: "NO_SESSION",
                message: "No active session to summarize",
            },
            createGuidance("ERROR", "interactive", [
                "Session tracking starts on first scope_guard_check",
            ])
        );
    }

    // Update edited files if provided
    if (edited_files) {
        for (const f of edited_files) {
            trackSessionAction("fileEdited", f);
        }
    }

    const summary = {
        ...currentSession,
        endTime: new Date().toISOString(),
        notes,
    };

    // Log summary to audit
    audit({
        action: "session_summary",
        status: "COMPLETED",
        details: JSON.stringify(summary),
    });

    // Reset session
    currentSession = null;
    scopesRead.clear();

    const warnings: string[] = [];
    if (summary.autoAssignments.length > 0) {
        warnings.push(`AUTO-ASSIGNED ${summary.autoAssignments.length} files - update scope files manually`);
    }

    return formatResponse(
        {
            status: "SESSION_COMPLETE",
            summary,
            action_required: summary.autoAssignments.length > 0
                ? "Update scope files with auto-assigned files"
                : null,
        },
        createGuidance("SESSION_COMPLETE", summary.agentMode, [
            "Session recorded in audit log",
            summary.autoAssignments.length > 0
                ? "MANUALLY update scope files with auto-assignments"
                : "No manual updates required",
        ], warnings)
    );
}

function handleScopeGuardStartSession(args: { agent_mode: string }): string {
    const { agent_mode } = args;

    if (!["interactive", "unattended"].includes(agent_mode)) {
        return formatResponse(
            {
                error: "INVALID_MODE",
                message: "agent_mode must be 'interactive' or 'unattended'",
            },
            createGuidance("ERROR", "interactive", [
                "Use agent_mode='interactive' for user-confirmed operations",
                "Use agent_mode='unattended' for autonomous operations (must call scope_guard_summary at end)",
            ])
        );
    }

    const session = startSession(agent_mode);

    audit({
        action: "session_start",
        status: "STARTED",
        agentMode: agent_mode,
        details: session.sessionId,
    });

    return formatResponse(
        {
            status: "SESSION_STARTED",
            session_id: session.sessionId,
            agent_mode,
            message: agent_mode === "unattended"
                ? "UNATTENDED MODE: Must call scope_guard_summary at end of session"
                : "INTERACTIVE MODE: User confirmation required for assignments",
        },
        createGuidance("SESSION_STARTED", agent_mode, [
            "Session started",
            "Use scope_guard_check before any file edit",
            agent_mode === "unattended"
                ? "MUST call scope_guard_summary before ending"
                : "User will be prompted for confirmations",
        ], agent_mode === "unattended" ? ["Remember to call scope_guard_summary at end"] : [])
    );
}

// ============================================================================
// Server Setup
// ============================================================================

const server = new Server(
    {
        name: "scope-guard",
        version: "3.0.0",
    },
    {
        capabilities: {
            tools: {},
        },
    }
);

server.setRequestHandler(ListToolsRequestSchema, async () => {
    return {
        tools: [
            {
                name: "mcp_scope-guard_scope_guard_start_session",
                description: "Start a new Scope Guard session. CALL THIS FIRST. Use agent_mode='interactive' for user-confirmed operations, or 'unattended' for autonomous operations (MUST call scope_guard_summary at end).",
                inputSchema: {
                    type: "object",
                    properties: {
                        agent_mode: {
                            type: "string",
                            enum: ["interactive", "unattended"],
                            description: "Agent mode: 'interactive' requires user confirmation, 'unattended' auto-assigns but MUST report in summary",
                        },
                    },
                    required: ["agent_mode"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_check",
                description: "MANDATORY before ANY file edit. Returns ALLOWED, BLOCKED, UNTRACKED, READ_ONLY, EXCLUDED, or BUNDLE_BLOCKED. Every response includes guidance for next steps.",
                inputSchema: {
                    type: "object",
                    properties: {
                        file_path: {
                            type: "string",
                            description: "Absolute or workspace-relative path to the file",
                        },
                        agent_mode: {
                            type: "string",
                            enum: ["interactive", "unattended"],
                            description: "Optional override for agent mode (defaults to session mode or 'interactive')",
                        },
                    },
                    required: ["file_path"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_get",
                description: "Get full details of a scope or bundle, including invariants, forbidden patterns, and mandatory reads.",
                inputSchema: {
                    type: "object",
                    properties: {
                        scope_id: {
                            type: "string",
                            description: "Scope ID or bundle ID to retrieve",
                        },
                    },
                    required: ["scope_id"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_read",
                description: "Mark a scope as read/acknowledged for this session. Required before editing files in that scope.",
                inputSchema: {
                    type: "object",
                    properties: {
                        scope_id: {
                            type: "string",
                            description: "Scope ID to mark as read",
                        },
                    },
                    required: ["scope_id"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_read_bundle",
                description: "Mark a bundle as read/acknowledged for this session. Required before editing files matching bundle patterns.",
                inputSchema: {
                    type: "object",
                    properties: {
                        bundle_id: {
                            type: "string",
                            description: "Bundle ID to mark as read",
                        },
                    },
                    required: ["bundle_id"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_list",
                description: "List all available scopes, bundles, global excludes, and read-only paths.",
                inputSchema: {
                    type: "object",
                    properties: {},
                    required: [],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_assign",
                description: "Assign an untracked file to a scope. In interactive mode, requires user confirmation. In unattended mode, auto-assigns and records for summary.",
                inputSchema: {
                    type: "object",
                    properties: {
                        file_path: {
                            type: "string",
                            description: "Path to the untracked file",
                        },
                        scope_id: {
                            type: "string",
                            description: "Scope ID to assign the file to",
                        },
                        justification: {
                            type: "string",
                            description: "Required if not using suggested scope - explain why",
                        },
                        agent_mode: {
                            type: "string",
                            enum: ["interactive", "unattended"],
                            description: "Override agent mode for this operation",
                        },
                    },
                    required: ["file_path", "scope_id"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_confirm",
                description: "Confirm or reject a pending assignment (interactive mode only).",
                inputSchema: {
                    type: "object",
                    properties: {
                        file_path: {
                            type: "string",
                            description: "Path of the file with pending assignment",
                        },
                        confirmed: {
                            type: "boolean",
                            description: "True to confirm, false to reject",
                        },
                    },
                    required: ["file_path", "confirmed"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_request_new_scope",
                description: "Request creation of a new scope. Will be rejected if paths overlap with existing scopes.",
                inputSchema: {
                    type: "object",
                    properties: {
                        scope_id: {
                            type: "string",
                            description: "Proposed scope ID (kebab-case)",
                        },
                        description: {
                            type: "string",
                            description: "Human-readable description of the scope",
                        },
                        module_paths: {
                            type: "array",
                            items: { type: "string" },
                            description: "Array of module paths this scope covers",
                        },
                        invariants: {
                            type: "array",
                            items: { type: "string" },
                            description: "Global invariants for this scope",
                        },
                    },
                    required: ["scope_id", "description", "module_paths"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_validate_code",
                description: "Validate proposed code against a scope's forbidden patterns.",
                inputSchema: {
                    type: "object",
                    properties: {
                        scope_id: {
                            type: "string",
                            description: "Scope ID to validate against",
                        },
                        code: {
                            type: "string",
                            description: "Code snippet to validate",
                        },
                    },
                    required: ["scope_id", "code"],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_audit_log",
                description: "View recent audit log entries.",
                inputSchema: {
                    type: "object",
                    properties: {
                        limit: {
                            type: "number",
                            description: "Maximum entries to return (default: 20)",
                        },
                    },
                    required: [],
                },
            },
            {
                name: "mcp_scope-guard_scope_guard_summary",
                description: "MANDATORY for unattended agents at end of session. Reports all scope operations, auto-assignments, and edited files.",
                inputSchema: {
                    type: "object",
                    properties: {
                        edited_files: {
                            type: "array",
                            items: { type: "string" },
                            description: "List of files that were edited during the session",
                        },
                        notes: {
                            type: "string",
                            description: "Optional notes about the session",
                        },
                    },
                    required: [],
                },
            },
        ],
    };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;

    try {
        let result: string;

        switch (name) {
            case "mcp_scope-guard_scope_guard_start_session":
                result = handleScopeGuardStartSession(args as { agent_mode: string });
                break;
            case "mcp_scope-guard_scope_guard_check":
                result = handleScopeGuardCheck(args as { file_path: string; agent_mode?: string });
                break;
            case "mcp_scope-guard_scope_guard_get":
                result = handleScopeGuardGet(args as { scope_id: string });
                break;
            case "mcp_scope-guard_scope_guard_read":
                result = handleScopeGuardRead(args as { scope_id: string });
                break;
            case "mcp_scope-guard_scope_guard_read_bundle":
                result = handleScopeGuardReadBundle(args as { bundle_id: string });
                break;
            case "mcp_scope-guard_scope_guard_list":
                result = handleScopeGuardList();
                break;
            case "mcp_scope-guard_scope_guard_assign":
                result = handleScopeGuardAssign(args as {
                    file_path: string;
                    scope_id: string;
                    justification?: string;
                    agent_mode?: string;
                });
                break;
            case "mcp_scope-guard_scope_guard_confirm":
                result = handleScopeGuardConfirm(args as { file_path: string; confirmed: boolean });
                break;
            case "mcp_scope-guard_scope_guard_request_new_scope":
                result = handleScopeGuardRequestNewScope(args as {
                    scope_id: string;
                    description: string;
                    module_paths: string[];
                    invariants?: string[];
                });
                break;
            case "mcp_scope-guard_scope_guard_validate_code":
                result = handleScopeGuardValidateCode(args as { scope_id: string; code: string });
                break;
            case "mcp_scope-guard_scope_guard_audit_log":
                result = handleScopeGuardAuditLog(args as { limit?: number });
                break;
            case "mcp_scope-guard_scope_guard_summary":
                result = handleScopeGuardSummary(args as { edited_files?: string[]; notes?: string });
                break;
            default:
                result = JSON.stringify({ error: "Unknown tool", name });
        }

        return {
            content: [{ type: "text", text: result }],
        };
    } catch (error) {
        return {
            content: [{ type: "text", text: JSON.stringify({ error: String(error) }) }],
            isError: true,
        };
    }
});

// ============================================================================
// Main
// ============================================================================

async function main() {
    log(`Starting with workspace: ${WORKSPACE_ROOT}`);

    config = loadConfig();
    log(`Loaded config ${JSON.stringify(config)}`);

    loadScopes();

    log(`Loaded ${scopes.size} scopes, ${Object.keys(config.bundles || {}).length} bundles`);
    log("MCP Server running (v3.0 - Agent Modes & Bundles)");

    const transport = new StdioServerTransport();
    await server.connect(transport);
}

main().catch((error) => {
    log(`Fatal error: ${error}`);
    process.exit(1);
});
