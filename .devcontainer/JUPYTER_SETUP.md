# Jupyter Setup for FishIT-Player Codespace

**Codespace Name:** verbose couscous  
**Setup Date:** 2026-01-09  
**Python Version:** Latest (automatically installed)  
**Jupyter Version:** Latest (automatically installed)

---

## Overview

The FishIT-Player devcontainer now includes a complete Jupyter setup for:

- Data analysis and visualization
- ObjectBox database inspection and debugging
- Pipeline data exploration
- Performance profiling
- Interactive development and experimentation

---

## Installation

Jupyter is **automatically installed AND started** when the Codespace is created:

1. **DevContainer Feature:** Python 3 (latest) with JupyterLab
2. **Post-Create Script:** Upgrades Jupyter to latest version
3. **Post-Start Script:** Automatically starts JupyterLab server on port 8888

**No manual installation or startup required!** JupyterLab is ready to use immediately.

---

## Quick Start

### ✨ Automatic Startup (Recommended)

JupyterLab starts automatically when the Codespace opens:

1. Wait for Codespace to fully initialize
2. Look for the **port forwarding notification** (usually appears automatically)
3. Click **"Open in Browser"** when the notification shows port 8888
4. JupyterLab will open in a new browser tab - no token required!

**Note:** If you don't see the notification, click the **PORTS** tab in VS Code and find port 8888, then click the globe icon.

### Option 1: JupyterLab Web Interface

Since JupyterLab is already running, you can access it via:
- Click the **PORTS** tab in VS Code
- Find port **8888** (labeled "JupyterLab")
- Click the **🌐 globe icon** to open in browser

### Option 2: Manual Start (if needed)

If JupyterLab is not running, you can start it manually:

```bash
# Start JupyterLab (modern interface)
jupyter lab --ip=0.0.0.0 --port=8888 --no-browser --allow-root

# Codespaces will automatically forward the port
# Click the "Open in Browser" button when prompted
```

### Option 3: Classic Jupyter Notebook

```bash
# Stop automatic JupyterLab first
pkill -f "jupyter lab"

# Start classic notebook interface
jupyter notebook --ip=0.0.0.0 --port=8888 --no-browser --allow-root
```

### Option 4: VS Code Native Jupyter

1. Create a new file with `.ipynb` extension
2. VS Code will automatically open it as a Jupyter notebook
3. Select Python kernel when prompted
4. Start coding!

---

## Common Use Cases

### 1. ObjectBox Database Inspection

```python
# Example: Inspect NX_* entities
import os
import sys

# Add project to path
sys.path.append('/workspaces/FishIT-Player')

# Import ObjectBox (if available in Python)
# Note: Primary inspection via Kotlin, but can analyze exported data

import json
import pandas as pd

# Load exported entity data
with open('/workspaces/FishIT-Player/.deprecated/docs-archive-2026-02-15/v2-subdirs/obx/_intermediate/entity_inventory.json') as f:
    entities = json.load(f)

# Convert to DataFrame
df = pd.DataFrame(entities)
print(df.head())
```

### 2. Pipeline Data Analysis

```python
# Example: Analyze pipeline ingest patterns
import json
import pandas as pd
import matplotlib.pyplot as plt

# Load ingest ledger data (when available)
# Analyze acceptance rates, rejection reasons, etc.

# Placeholder for actual data
data = {
    'decision': ['ACCEPTED', 'REJECTED', 'SKIPPED', 'ACCEPTED'],
    'reason': ['NEW_WORK', 'TOO_SHORT', 'ALREADY_EXISTS', 'NEW_VARIANT'],
    'source': ['TELEGRAM', 'TELEGRAM', 'XTREAM', 'TELEGRAM']
}

df = pd.DataFrame(data)
print(df['decision'].value_counts())
```

### 3. Performance Profiling

```python
# Example: Profile batch processing performance
import time
import pandas as pd

# Simulate batch size analysis
batch_sizes = [35, 50, 100, 200, 400, 600]
processing_times = []

# Actual profiling would use real data
for size in batch_sizes:
    # Placeholder timing
    processing_times.append(size * 0.1)

df = pd.DataFrame({
    'batch_size': batch_sizes,
    'time_seconds': processing_times
})

print(df)

# Plot
import matplotlib.pyplot as plt
plt.plot(df['batch_size'], df['time_seconds'])
plt.xlabel('Batch Size')
plt.ylabel('Processing Time (s)')
plt.title('Batch Size vs Processing Time')
plt.show()
```

### 4. Contract Validation

```python
# Example: Validate workKey format
import re

def validate_work_key(key: str) -> bool:
    """
    Validate workKey format: <workType>:<slug>:<year|LIVE>
    """
    pattern = r'^(movie|episode|series|clip|live|audiobook):[a-z0-9-]+:(LIVE|\d{4})$'
    return bool(re.match(pattern, key))

# Test cases
test_keys = [
    'movie:the-matrix:1999',
    'episode:breaking-bad:s01e01',
    'live:sport1:LIVE',
    'INVALID:Key:Format'  # Should fail
]

for key in test_keys:
    valid = validate_work_key(key)
    print(f"{key}: {'✓ VALID' if valid else '✗ INVALID'}")
```

---

## Installed Packages

The Jupyter installation includes:

- **jupyter** - Core Jupyter notebook
- **jupyterlab** - Modern JupyterLab interface
- **notebook** - Classic notebook interface
- **ipywidgets** - Interactive widgets

### Additional Packages (Install as needed)

```bash
# Data analysis
pip3 install --user pandas numpy matplotlib seaborn

# Machine learning (for embeddings work)
pip3 install --user scikit-learn

# Android debugging
pip3 install --user adb-shell

# Database tools
pip3 install --user sqlalchemy
```

---

## VS Code Integration

The devcontainer includes VS Code Jupyter extensions:

1. **ms-python.python** - Python language support
2. **ms-toolsai.jupyter** - Jupyter notebook support
3. **ms-toolsai.jupyter-keymap** - Jupyter keybindings
4. **ms-toolsai.jupyter-renderers** - Enhanced rendering

### Using VS Code Notebooks

1. Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on Mac)
2. Type "Jupyter: Create New Jupyter Notebook"
3. Select Python kernel
4. Start coding!

**Benefits:**

- No separate server needed
- Integrated with VS Code UI
- Git-friendly (notebooks are tracked)
- Copilot works in notebooks!

---

## Troubleshooting

### Cannot Connect to JupyterLab

**Symptom:** Error message "We were unable to connect to your codespace in JupyterLab!"

**Solutions:**

1. **Check if JupyterLab is running:**
   ```bash
   ps aux | grep jupyter
   ```

2. **Manually start JupyterLab if not running:**
   ```bash
   jupyter lab --ip=0.0.0.0 --port=8888 --no-browser --allow-root
   ```

3. **Check port forwarding:**
   - Open the **PORTS** tab in VS Code
   - Verify port 8888 is listed and forwarded
   - If not, add it manually: Click "+" and enter "8888"

4. **View JupyterLab logs:**
   ```bash
   cat /tmp/jupyter.log
   ```

5. **Restart JupyterLab:**
   ```bash
   pkill -f "jupyter lab"
   nohup jupyter lab --ip=0.0.0.0 --port=8888 --no-browser --allow-root > /tmp/jupyter.log 2>&1 &
   ```

### Jupyter Command Not Found

```bash
# Check if Jupyter is in PATH
which jupyter

# If not found, add to PATH
export PATH="$HOME/.local/bin:$PATH"

# Or reinstall
pip3 install --user --upgrade jupyter jupyterlab
```

### Port Already in Use

```bash
# Kill existing JupyterLab process
pkill -f "jupyter lab"

# Or use a different port
jupyter lab --ip=0.0.0.0 --port=8889 --no-browser --allow-root
```

### Python Kernel Not Found

```bash
# Install ipykernel
pip3 install --user ipykernel

# Register kernel
python3 -m ipykernel install --user --name=python3
```

### VS Code Can't Find Jupyter

1. Reload VS Code: `Ctrl+Shift+P` → "Developer: Reload Window"
2. Check extensions are installed
3. Select Python interpreter: `Ctrl+Shift+P` → "Python: Select Interpreter"

### Authentication Token Issues

**Note:** GitHub Codespaces handles authentication automatically. You should NOT see token prompts.

If you see a token prompt:
- This usually means JupyterLab wasn't started with the correct flags
- Make sure you use `--no-browser` and `--allow-root` flags
- The `--ip=0.0.0.0` flag is REQUIRED for Codespaces proxy access

---

## Best Practices

### 1. Use Virtual Environments

```bash
# Create venv for project-specific packages
python3 -m venv ~/venvs/fishit
source ~/venvs/fishit/bin/activate

# Install packages in venv
pip install pandas matplotlib
```

### 2. Save Notebooks in `notebooks/` Directory

```bash
mkdir -p /workspaces/FishIT-Player/notebooks
cd /workspaces/FishIT-Player/notebooks

# Create notebook here
jupyter notebook
```

**Note:** Add `notebooks/` to `.gitignore` if needed for scratch work.

### 3. Export Results as Markdown

```bash
# Convert notebook to Markdown for documentation
jupyter nbconvert --to markdown my-analysis.ipynb
```

### 4. Use Markdown Cells for Documentation

```markdown
# Analysis: NX Entity Migration Performance

**Date:** 2026-01-09  
**Author:** Your Name

## Objective
Analyze batch size impact on migration performance.

## Findings
- Batch size 35 (FireTV): 120ms per item
- Batch size 200 (Phone): 45ms per item
```

---

## Integration with OBX PLATIN Refactor

Jupyter can be used throughout the OBX PLATIN refactoring phases:

### Phase 0 (Completed)
- ✓ Validate key format contracts
- ✓ Test deterministic key generation

### Phase 1 (Repository Implementation)
- Prototype repository queries
- Test ObjectBox query performance
- Validate uniqueness constraints

### Phase 2 (Ingest Path)
- Analyze ingest ledger patterns
- Visualize rejection reasons
- Monitor classification accuracy

### Phase 3 (Migration Worker)
- Profile batch processing
- Monitor memory usage
- Validate migration progress

### Phase 4 (Dual-Read UI)
- Compare legacy vs. NX query performance
- Visualize data quality metrics
- Debug UI state issues

### Phase 5-6 (Production)
- Monitor production metrics
- Analyze user behavior
- Debug production issues

---

## Example Notebooks

### Creating Your First Notebook

1. Create `notebooks/hello-jupyter.ipynb`
2. Add cells:

```python
# Cell 1: Import libraries
import sys
import os

print(f"Python version: {sys.version}")
print(f"Working directory: {os.getcwd()}")
```

```python
# Cell 2: Test data analysis
import pandas as pd

data = {
    'entity': ['NX_Work', 'NX_WorkSourceRef', 'NX_WorkVariant'],
    'count': [1234, 2345, 3456]
}

df = pd.DataFrame(data)
print(df)
```

```python
# Cell 3: Visualize
import matplotlib.pyplot as plt

df.plot(x='entity', y='count', kind='bar')
plt.title('NX Entity Counts')
plt.show()
```

---

## Resources

- **Jupyter Documentation:** https://jupyter.org/documentation
- **JupyterLab Documentation:** https://jupyterlab.readthedocs.io/
- **VS Code Jupyter:** https://code.visualstudio.com/docs/datascience/jupyter-notebooks
- **Pandas Documentation:** https://pandas.pydata.org/docs/
- **Matplotlib Documentation:** https://matplotlib.org/stable/contents.html

---

## Advanced: Custom Jupyter Kernels

For specialized analysis, you can create custom kernels:

### Kotlin Kernel (For ObjectBox)

```bash
# Install Kotlin kernel (if needed)
pip3 install --user kotlin-jupyter-kernel
```

### Gradle Integration

```bash
# Run Gradle tasks from Jupyter
!./gradlew :core:persistence:test --tests NxKeyGeneratorTest
```

---

## Maintenance

### Upgrade Jupyter

```bash
# Upgrade to latest version
pip3 install --user --upgrade jupyter jupyterlab notebook

# Verify version
jupyter --version
```

### Clean Cache

```bash
# Clear Jupyter cache
jupyter cache clean
```

### Reset Environment

```bash
# Remove all user-installed packages
rm -rf ~/.local/lib/python*/site-packages/

# Reinstall Jupyter
bash .devcontainer/post-create.sh
```

---

## Security Notes

⚠️ **Important:** Jupyter notebooks can execute arbitrary code.

- Never run untrusted notebooks
- Review all cells before execution
- Be careful with API keys and credentials
- Use environment variables for secrets

---

## Feedback

If you encounter issues with the Jupyter setup:

1. Check this README's troubleshooting section
2. Review `.devcontainer/post-create.sh` logs
3. File an issue with logs and error messages

---

**Last Updated:** 2026-01-09  
**Maintainer:** Development Team  
**Status:** Active
