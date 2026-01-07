/**
 * Task Chain Helper Script
 * 
 * Helper script for managing task chains in GitHub Issues.
 * Fetches sub-issues, determines next task, and manages status updates.
 */

const https = require('https');

// Environment variables
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const GITHUB_REPOSITORY = process.env.GITHUB_REPOSITORY;
const PARENT_ISSUE = process.env.PARENT_ISSUE || '573';

if (!GITHUB_TOKEN || !GITHUB_REPOSITORY) {
  console.error('Error: GITHUB_TOKEN and GITHUB_REPOSITORY environment variables are required');
  process.exit(1);
}

/**
 * Make a GitHub API request
 */
function githubApi(method, path, data = null) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'api.github.com',
      path: path,
      method: method,
      headers: {
        'Authorization': `Bearer ${GITHUB_TOKEN}`,
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'Task-Chain-Agent',
      }
    };

    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(body || '{}'));
          } catch (e) {
            resolve({});
          }
        } else {
          reject(new Error(`GitHub API ${method} ${path} failed: ${res.statusCode} ${body}`));
        }
      });
    });

    req.on('error', reject);
    
    if (data) {
      req.write(JSON.stringify(data));
    }
    
    req.end();
  });
}

/**
 * Make a GitHub GraphQL API request
 */
function graphqlApi(query, variables = {}) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify({ query, variables });
    
    const options = {
      hostname: 'api.github.com',
      path: '/graphql',
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${GITHUB_TOKEN}`,
        'Accept': 'application/vnd.github+json',
        'User-Agent': 'Task-Chain-Agent',
        'GraphQL-Features': 'issues_copilot_assignment_api_support',
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data)
      }
    };

    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            const result = JSON.parse(body);
            if (result.errors) {
              reject(new Error(`GraphQL errors: ${JSON.stringify(result.errors)}`));
            } else {
              resolve(result.data);
            }
          } catch (e) {
            resolve({});
          }
        } else {
          reject(new Error(`GitHub GraphQL API failed: ${res.statusCode} ${body}`));
        }
      });
    });

    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

/**
 * Get issue details
 */
async function getIssue(issueNumber) {
  const [owner, repo] = GITHUB_REPOSITORY.split('/');
  return await githubApi('GET', `/repos/${owner}/${repo}/issues/${issueNumber}`);
}

/**
 * Parse sub-issue numbers from issue body
 * Looks for patterns like #123, GH-123, or full URLs
 */
function parseSubIssues(issueBody) {
  if (!issueBody) return [];
  
  const subIssues = new Set();
  
  // Pattern 1: #123
  const hashPattern = /#(\d+)/g;
  let match;
  while ((match = hashPattern.exec(issueBody)) !== null) {
    subIssues.add(parseInt(match[1], 10));
  }
  
  // Pattern 2: GH-123
  const ghPattern = /GH-(\d+)/gi;
  while ((match = ghPattern.exec(issueBody)) !== null) {
    subIssues.add(parseInt(match[1], 10));
  }
  
  // Pattern 3: Full GitHub URLs
  const urlPattern = /github\.com\/[^\/]+\/[^\/]+\/issues\/(\d+)/g;
  while ((match = urlPattern.exec(issueBody)) !== null) {
    subIssues.add(parseInt(match[1], 10));
  }
  
  return Array.from(subIssues).sort((a, b) => a - b);
}

/**
 * Get all sub-issues of a parent issue
 */
async function getSubIssues(parentIssueNumber) {
  console.log(`Fetching parent issue #${parentIssueNumber}...`);
  const parentIssue = await getIssue(parentIssueNumber);
  
  const subIssueNumbers = parseSubIssues(parentIssue.body);
  console.log(`Found ${subIssueNumbers.length} sub-issues: ${subIssueNumbers.join(', ')}`);
  
  // Fetch details for all sub-issues
  const subIssues = [];
  for (const num of subIssueNumbers) {
    try {
      const issue = await getIssue(num);
      subIssues.push(issue);
    } catch (error) {
      console.error(`Warning: Could not fetch issue #${num}: ${error.message}`);
    }
  }
  
  return subIssues;
}

/**
 * Check if issue has a specific label
 */
function hasLabel(issue, labelName) {
  return issue.labels && issue.labels.some(l => l.name === labelName);
}

/**
 * Find the next open task
 */
async function findNextTask(parentIssueNumber) {
  const subIssues = await getSubIssues(parentIssueNumber);
  
  if (subIssues.length === 0) {
    console.log('No sub-issues found');
    return null;
  }
  
  // Find first task that is not done
  for (const issue of subIssues) {
    const labels = (issue.labels || []).map(l => l.name);
    const state = issue.state;
    
    console.log(`Issue #${issue.number}: state=${state}, labels=[${labels.join(', ')}]`);
    
    // Skip if closed or platinum-done
    if (state === 'closed' || hasLabel(issue, 'platinum-done')) {
      console.log(`  ✅ Skipping #${issue.number} - completed`);
      continue;
    }
    
    // Return first open task
    console.log(`  🎯 Found next task: #${issue.number}`);
    return {
      number: issue.number,
      title: issue.title,
      state: state,
      labels: labels,
      url: issue.html_url,
      inProgress: hasLabel(issue, 'in-progress'),
      needsReview: hasLabel(issue, 'needs-review'),
      readyForAgent: hasLabel(issue, 'ready-for-agent')
    };
  }
  
  console.log('All tasks completed! 🎉');
  return null;
}

/**
 * Add labels to an issue
 */
async function addLabels(issueNumber, labels) {
  const [owner, repo] = GITHUB_REPOSITORY.split('/');
  console.log(`Adding labels to #${issueNumber}: ${labels.join(', ')}`);
  return await githubApi('POST', `/repos/${owner}/${repo}/issues/${issueNumber}/labels`, {
    labels: labels
  });
}

/**
 * Remove a label from an issue
 */
async function removeLabel(issueNumber, label) {
  const [owner, repo] = GITHUB_REPOSITORY.split('/');
  console.log(`Removing label from #${issueNumber}: ${label}`);
  try {
    return await githubApi('DELETE', `/repos/${owner}/${repo}/issues/${issueNumber}/labels/${encodeURIComponent(label)}`);
  } catch (error) {
    // Ignore if label doesn't exist
    if (error.message.includes('404')) {
      console.log(`Label ${label} not found on #${issueNumber}, ignoring`);
    } else {
      throw error;
    }
  }
}

/**
 * Close an issue
 */
async function closeIssue(issueNumber) {
  const [owner, repo] = GITHUB_REPOSITORY.split('/');
  console.log(`Closing issue #${issueNumber}`);
  return await githubApi('PATCH', `/repos/${owner}/${repo}/issues/${issueNumber}`, {
    state: 'closed'
  });
}

/**
 * Post a comment on an issue
 */
async function postComment(issueNumber, body) {
  const [owner, repo] = GITHUB_REPOSITORY.split('/');
  console.log(`Posting comment to #${issueNumber}`);
  return await githubApi('POST', `/repos/${owner}/${repo}/issues/${issueNumber}/comments`, {
    body: body
  });
}

/**
 * Assign Copilot to an issue with custom agent using GraphQL API
 */
async function assignCopilot(issueNumber, customAgent = 'v2_codespace_agent') {
  const [owner, repo] = GITHUB_REPOSITORY.split('/');
  console.log(`Assigning Copilot to issue #${issueNumber} with custom agent: ${customAgent}`);
  
  try {
    // First, get the issue node ID via REST
    const issue = await getIssue(issueNumber);
    const issueNodeId = issue.node_id;
    
    // Use GraphQL to assign Copilot with custom agent
    const graphqlQuery = `
      mutation AssignCopilotToIssue($issueId: ID!, $agent: String) {
        assignCopilotToIssue(input: {
          issueId: $issueId,
          customAgent: $agent
        }) {
          issue {
            id
            number
          }
        }
      }
    `;
    
    const result = await graphqlApi(graphqlQuery, {
      issueId: issueNodeId,
      agent: customAgent
    });
    
    console.log(`Successfully assigned Copilot with custom agent ${customAgent} to issue #${issueNumber}`);
    return result;
  } catch (error) {
    console.warn(`GraphQL assignment failed: ${error.message}`);
    console.log(`Falling back to REST API assignment...`);
    
    // Fallback to REST API without custom agent
    try {
      return await githubApi('POST', `/repos/${owner}/${repo}/issues/${issueNumber}/assignees`, {
        assignees: ['copilot']
      });
    } catch (restError) {
      console.error(`REST API fallback also failed: ${restError.message}`);
      throw restError;
    }
  }
}

/**
 * Check if all sub-issues are completed
 */
async function checkAllCompleted(parentIssueNumber) {
  const subIssues = await getSubIssues(parentIssueNumber);
  
  if (subIssues.length === 0) {
    return false;
  }
  
  for (const issue of subIssues) {
    if (issue.state !== 'closed' && !hasLabel(issue, 'platinum-done')) {
      return false;
    }
  }
  
  return true;
}

/**
 * Get PR details
 */
async function getPR(prNumber) {
  const [owner, repo] = GITHUB_REPOSITORY.split('/');
  return await githubApi('GET', `/repos/${owner}/${repo}/pulls/${prNumber}`);
}

/**
 * Extract issue numbers from text (PR body, title, etc.)
 */
function extractIssueNumbers(text) {
  if (!text) return [];
  
  const issues = new Set();
  
  // Pattern: Fixes #123, Closes #123, Resolves #123
  const keywordPattern = /(?:fix(?:es|ed)?|close(?:s|d)?|resolve(?:s|d)?)\s+#(\d+)/gi;
  let match;
  while ((match = keywordPattern.exec(text)) !== null) {
    issues.add(parseInt(match[1], 10));
  }
  
  // Pattern: #123 at start of line or after whitespace
  const hashPattern = /(?:^|\s)#(\d+)/g;
  while ((match = hashPattern.exec(text)) !== null) {
    issues.add(parseInt(match[1], 10));
  }
  
  return Array.from(issues);
}

/**
 * Set GitHub Actions output
 */
function setOutput(name, value) {
  const output = process.env.GITHUB_OUTPUT;
  if (output) {
    const fs = require('fs');
    fs.appendFileSync(output, `${name}=${value}\n`);
  }
  console.log(`Output: ${name}=${value}`);
}

/**
 * Main function
 */
async function main() {
  const command = process.argv[2];
  
  try {
    switch (command) {
      case 'find-next':
        const nextTask = await findNextTask(PARENT_ISSUE);
        if (nextTask) {
          setOutput('has_next', 'true');
          setOutput('next_issue', nextTask.number);
          setOutput('next_title', nextTask.title);
          setOutput('next_url', nextTask.url);
          setOutput('next_in_progress', nextTask.inProgress ? 'true' : 'false');
          setOutput('next_needs_review', nextTask.needsReview ? 'true' : 'false');
          setOutput('next_ready_for_agent', nextTask.readyForAgent ? 'true' : 'false');
        } else {
          setOutput('has_next', 'false');
          setOutput('next_issue', '');
        }
        break;
        
      case 'check-completed':
        const allCompleted = await checkAllCompleted(PARENT_ISSUE);
        setOutput('all_completed', allCompleted ? 'true' : 'false');
        break;
        
      case 'add-labels':
        const issueNum = process.argv[3];
        const labelsToAdd = process.argv.slice(4);
        if (!issueNum || labelsToAdd.length === 0) {
          throw new Error('Usage: add-labels <issue-number> <label1> [label2...]');
        }
        await addLabels(issueNum, labelsToAdd);
        break;
        
      case 'remove-label':
        const issueNum2 = process.argv[3];
        const labelToRemove = process.argv[4];
        if (!issueNum2 || !labelToRemove) {
          throw new Error('Usage: remove-label <issue-number> <label>');
        }
        await removeLabel(issueNum2, labelToRemove);
        break;
        
      case 'close-issue':
        const issueNum3 = process.argv[3];
        if (!issueNum3) {
          throw new Error('Usage: close-issue <issue-number>');
        }
        await closeIssue(issueNum3);
        break;
        
      case 'post-comment':
        const issueNum4 = process.argv[3];
        const commentBody = process.argv[4];
        if (!issueNum4 || !commentBody) {
          throw new Error('Usage: post-comment <issue-number> <body>');
        }
        await postComment(issueNum4, commentBody);
        break;
        
      case 'assign-copilot':
        const issueNumAssign = process.argv[3];
        const customAgent = process.argv[4] || 'v2_codespace_agent';
        if (!issueNumAssign) {
          throw new Error('Usage: assign-copilot <issue-number> [custom-agent]');
        }
        await assignCopilot(issueNumAssign, customAgent);
        break;
        
      case 'extract-issue-from-pr':
        const prNum = process.argv[3];
        if (!prNum) {
          throw new Error('Usage: extract-issue-from-pr <pr-number>');
        }
        const pr = await getPR(prNum);
        const issueNumbers = extractIssueNumbers(`${pr.title} ${pr.body}`);
        if (issueNumbers.length > 0) {
          setOutput('issue_number', issueNumbers[0].toString());
          console.log(`Found issue #${issueNumbers[0]} in PR #${prNum}`);
        } else {
          setOutput('issue_number', '');
          console.log(`No issue found in PR #${prNum}`);
        }
        break;
        
      case 'check-issue-label':
        const issueNum5 = process.argv[3];
        const labelToCheck = process.argv[4];
        if (!issueNum5 || !labelToCheck) {
          throw new Error('Usage: check-issue-label <issue-number> <label>');
        }
        const issue5 = await getIssue(issueNum5);
        const hasTheLabel = hasLabel(issue5, labelToCheck);
        setOutput('has_label', hasTheLabel ? 'true' : 'false');
        console.log(`Issue #${issueNum5} ${hasTheLabel ? 'has' : 'does not have'} label: ${labelToCheck}`);
        break;
        
      default:
        console.error(`Unknown command: ${command}`);
        console.error('Available commands: find-next, check-completed, add-labels, remove-label, close-issue, post-comment, assign-copilot, extract-issue-from-pr, check-issue-label');
        process.exit(1);
    }
    
    console.log('Success!');
  } catch (error) {
    console.error(`Error: ${error.message}`);
    process.exit(1);
  }
}

main();
