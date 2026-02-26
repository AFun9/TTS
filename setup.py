#!/usr/bin/env python3

"""
Sherpa TTS Android - Setup script for development tools
"""

import os
import subprocess
import sys
from pathlib import Path

def run_command(cmd, cwd=None):
    """Run a shell command and return the result"""
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            cwd=cwd,
            capture_output=True,
            text=True,
            check=True
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"Command failed: {cmd}")
        print(f"Error: {e.stderr}")
        return None

def setup_android_development():
    """Setup Android development environment"""
    print("Setting up Android development environment...")

    # Check if Android SDK is available
    android_home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not android_home:
        print("Warning: ANDROID_HOME or ANDROID_SDK_ROOT not set")
        print("Please install Android SDK and set the environment variables")
        return False

    print(f"Android SDK found at: {android_home}")

    # Check for required tools
    required_tools = ['javac', 'java', 'adb']
    for tool in required_tools:
        if not run_command(f"which {tool}"):
            print(f"Warning: {tool} not found in PATH")
        else:
            print(f"✓ {tool} found")

    # Check for Android SDK components
    sdk_tools = [
        "platform-tools",
        "build-tools",
        "platforms/android-34",
        "ndk/26.0.10792818"
    ]

    for tool in sdk_tools:
        tool_path = Path(android_home) / tool
        if tool_path.exists():
            print(f"✓ {tool} found")
        else:
            print(f"Warning: {tool} not found")

    return True

def setup_python_dependencies():
    """Setup Python dependencies for development"""
    print("Setting up Python dependencies...")

    try:
        import sherpa_onnx
        print(f"✓ sherpa-onnx {sherpa_onnx.__version__} found")
    except ImportError:
        print("Installing sherpa-onnx...")
        run_command("pip install sherpa-onnx")

    # Check other development dependencies
    dev_deps = ['numpy', 'click', 'pytest']
    for dep in dev_deps:
        try:
            __import__(dep.replace('-', '_'))
            print(f"✓ {dep} found")
        except ImportError:
            print(f"Installing {dep}...")
            run_command(f"pip install {dep}")

def create_example_configs():
    """Create example configuration files"""
    print("Creating example configuration files...")

    # Create example config
    config_dir = Path("examples")
    config_dir.mkdir(exist_ok=True)

    example_config = """
{
  "model": {
    "model_path": "models/russian_vits.onnx",
    "tokens_path": "models/ru_tokens.txt",
    "lexicon_path": "lexicons/russian_to_english.txt",
    "data_dir": "models/espeak-ng-data"
  },
  "runtime": {
    "speaker_id": 0,
    "speed": 1.0,
    "debug": false
  },
  "output": {
    "default_format": "wav",
    "sample_rate": 22050
  }
}
"""

    with open(config_dir / "config.json", 'w', encoding='utf-8') as f:
        f.write(example_config.strip())

    print("✓ Example config created: examples/config.json")

    # Create example lexicon
    lexicon_dir = Path("lexicons")
    lexicon_dir.mkdir(exist_ok=True)

    example_lexicon = """# Russian VITS model -> English pronunciation
# Format: english_word russian_phonemes...
hello х э л о
world в о р л д
thank т х æ ŋ к
you ј у
good г у д
"""

    with open(lexicon_dir / "russian_to_english.txt", 'w', encoding='utf-8') as f:
        f.write(example_lexicon)

    print("✓ Example lexicon created: lexicons/russian_to_english.txt")

def main():
    """Main setup function"""
    print("Sherpa TTS Android - Development Setup")
    print("=" * 50)

    # Setup Android development
    android_ok = setup_android_development()
    if not android_ok:
        print("\nAndroid setup incomplete. Please complete Android SDK setup first.")
        sys.exit(1)

    # Setup Python dependencies
    setup_python_dependencies()

    # Create example files
    create_example_configs()

    print("\n" + "=" * 50)
    print("Setup complete!")
    print("\nNext steps:")
    print("1. Import the project into Android Studio")
    print("2. Build and run the Android app")
    print("3. Test with example models and lexicons")
    print("4. Refer to docs/ for detailed documentation")

if __name__ == "__main__":
    main()