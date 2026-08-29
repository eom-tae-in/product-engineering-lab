#!/bin/bash
# savePick Agent · Skill 설치 스크립트
# 사용법: 프로젝트 루트(save-pick)에서 실행
#   bash claude-setup/install.sh

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/claude-setup"
DST="$ROOT/.claude"

mkdir -p "$DST/agents" "$DST/commands" "$DST/skills"
cp "$SRC/agents/"*.md "$DST/agents/"
cp "$SRC/commands/"*.md "$DST/commands/"
cp -R "$SRC/skills/." "$DST/skills/"

echo "설치 완료"
echo "  Agent   : $(ls -1 "$DST/agents" | wc -l | tr -d ' ')개"
echo "  Command : $(ls -1 "$DST/commands" | wc -l | tr -d ' ')개"
echo "  Skill   : $(ls -1 "$DST/skills" | wc -l | tr -d ' ')개"
echo ""
echo "확인: claude 실행 후 /agents, /help 로 목록을 확인하세요."
echo "설치가 끝나면 claude-setup 폴더는 지워도 됩니다."
