#!/usr/bin/env python3
"""
Sherpa TTS Wrapper - Demo Script

This script demonstrates how to use the Sherpa TTS framework
for cross-language speech synthesis.
"""

import os
import sys
import json
from pathlib import Path

# Add the project root to Python path
sys.path.insert(0, str(Path(__file__).parent.parent))

def create_demo_config():
    """Create a demo configuration file"""
    config = {
        "model": {
            "model_path": "models/russian_vits.onnx",
            "tokens_path": "models/ru_tokens.txt",
            "lexicon_path": "lexicons/russian_to_english.txt",
            "data_dir": "models/espeak-ng-data"
        },
        "runtime": {
            "speaker_id": 0,
            "speed": 1.0,
            "debug": True
        },
        "output": {
            "default_format": "wav",
            "sample_rate": 22050
        }
    }

    with open("examples/demo_config.json", "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)

    print("✓ Demo config created: examples/demo_config.json")

def demonstrate_lexicon_format():
    """Show how lexicon files are structured"""
    print("\n📖 Lexicon File Format:")
    print("=" * 50)

    lexicon_content = """
# This is a comment
hello х э л о        # English -> Russian phonemes
world в о р л д      # 'world' pronounced with Russian sounds
thank т х æ ŋ к     # 'thank' in Russian phoneme space

# Empty lines are ignored
# Invalid lines (unknown phonemes) will be skipped
"""

    print(lexicon_content.strip())

    print("\n🔍 Key Points:")
    print("- Each line: word phoneme1 phoneme2 phoneme3...")
    print("- Phonemes must exist in tokens.txt")
    print("- Lines starting with # are comments")
    print("- Empty lines are ignored")
    print("- Unknown phonemes cause line to be skipped")

def show_project_structure():
    """Display the project structure"""
    print("\n📁 Project Structure:")
    print("=" * 50)

    structure = """
sherpa-tts-wrapper/
├── docs/                    # Documentation
│   ├── requirements.md     # Requirements specification
│   ├── design.md           # Design documentation
│   └── user_guide.md       # User manual
├── android/                # Android application
│   └── app/src/main/
│       ├── java/           # Kotlin/Java source code
│       ├── cpp/            # JNI implementation
│       └── res/            # Android resources
├── examples/               # Example configurations
├── lexicons/               # Sample lexicon files
├── models/                 # Model files directory
├── scripts/                # Utility scripts
└── README.md              # Main documentation
"""

    print(structure)

def demonstrate_cross_language_usage():
    """Show how cross-language synthesis works"""
    print("\n🌍 Cross-Language Synthesis Example:")
    print("=" * 50)

    print("Scenario: Using Russian-trained VITS model for English text")
    print()

    # Example text processing
    english_text = "Hello world"
    print(f"Input text: {english_text}")

    # Simulate lexicon lookup
    lexicon = {
        "hello": "х э л о",
        "world": "в о р л д"
    }

    print("\nLexicon lookup:")
    for word in english_text.lower().split():
        if word in lexicon:
            print(f"  {word} → {lexicon[word]} (found in lexicon)")
        else:
            print(f"  {word} → (not in lexicon, use default phonemizer)")

    print("\nResult:")
    print("- 'hello' uses Russian phonemes: х э л о")
    print("- 'world' uses Russian phonemes: в о р л д")
    print("- Output speech has Russian accent but correct English words")

def show_development_workflow():
    """Show the development workflow"""
    print("\n🔧 Development Workflow:")
    print("=" * 50)

    steps = [
        "1. Setup development environment",
        "   python setup.py",
        "",
        "2. Build Android JNI library",
        "   cd android && ./gradlew build",
        "",
        "3. Test lexicon functionality",
        "   python examples/demo.py",
        "",
        "4. Run Android app",
        "   Connect device and click 'Run' in Android Studio",
        "",
        "5. Import test models and lexicons",
        "   Use app's model manager to import files",
        "",
        "6. Test cross-language synthesis",
        "   Input text and verify pronunciation"
    ]

    for step in steps:
        print(step)

def main():
    """Main demo function"""
    print("🎤 Sherpa TTS Wrapper - Demo")
    print("=" * 60)

    # Create demo files
    create_demo_config()

    # Show project structure
    show_project_structure()

    # Demonstrate lexicon format
    demonstrate_lexicon_format()

    # Show cross-language usage
    demonstrate_cross_language_usage()

    # Show development workflow
    show_development_workflow()

    print("\n" + "=" * 60)
    print("🎉 Demo complete!")
    print("\nNext steps:")
    print("1. Review the documentation in docs/")
    print("2. Set up your development environment")
    print("3. Start building the Android application")
    print("4. Test with your own models and lexicons")

if __name__ == "__main__":
    main()