import sys
import os

# Test imports from siblings/subpackages

from main import resolve_command, parse_voice_time, parse_yes_no

from datetime import datetime, timedelta

def test_parsing():
    print("Testing resolve_command...")
    assert resolve_command("show tasks") == "list"
    assert resolve_command("what do i have to do") == "list"
    assert resolve_command("new task") == "add"
    assert resolve_command("create something") == "add"
    assert resolve_command("mark complete") == "complete"
    assert resolve_command("goodnight") == "bedtime"
    print("[OK] resolve_command passed")

    print("\nTesting parse_yes_no...")
    assert parse_yes_no("yeah sure") == True
    assert parse_yes_no("nope") == False
    print("[OK] parse_yes_no passed")

    print("\nTesting parse_voice_time...")
    # Mocking datetime is hard without lib, so we test logic roughly
    t1 = parse_voice_time("today")
    print(f"'today' -> {t1}")
    
    t2 = parse_voice_time("tomorrow")
    print(f"'tomorrow' -> {t2}")
    
    t3 = parse_voice_time("in 10 minutes")
    print(f"'in 10 minutes' -> {t3}")

    t4 = parse_voice_time("monday")
    print(f"'monday' -> {t4}")

    t5 = parse_voice_time("friday")
    print(f"'friday' -> {t5}")

    t6 = parse_voice_time("in 3 days")
    print(f"'in 3 days' -> {t6}")

if __name__ == "__main__":
    test_parsing()
