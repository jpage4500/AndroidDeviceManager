#!/bin/bash
#
# Delete local branches that have already been fully merged into the integration
# branch. Branches are listed in two sections (not-merged vs orphaned) first,
# then you get one prompt to delete every orphan at once.
#
# Portable: it operates on the git repo this script lives in, so it can be
# copied into any project -- change only the branch name passed in at the bottom.

PROTECTED_BRANCHES="develop staging production main master"

# never delete these, no matter what the merge check says
# exact-name match -- a substring grep would also match e.g. feature/develop-crash-fix
function isProtected () {
  local branch="$1" main_branch="$2" protected

  # the branch we are comparing against is protected by definition: it is
  # trivially "merged into itself", so the merge check alone would green-light it
  [[ -n "$main_branch" && "$branch" == "$main_branch" ]] && return 0

  for protected in $PROTECTED_BRANCHES
  do
    [[ "$branch" == "$protected" ]] && return 0
  done
  return 1
}

# check if the given branch is merged into the dest branch
# @see https://stackoverflow.com/a/49434212/1777346
function isMerged () {
  local merge_destination_branch="$1"
  local merge_source_branch="$2"
  local merge_base merge_source_current_commit

  merge_base=$(git merge-base "$merge_destination_branch" "$merge_source_branch" 2>/dev/null) || return 1
  merge_source_current_commit=$(git rev-parse --verify "$merge_source_branch" 2>/dev/null) || return 1

  # empty values must never compare equal -- that would report "merged" for a branch we failed to resolve
  [[ -n "$merge_base" && -n "$merge_source_current_commit" ]] || return 1

  if [[ "$merge_base" == "$merge_source_current_commit" ]]; then
      # merged!
      return 0
  else
      # not merged
      return 1
  fi
}

# single choke point for deletion: re-verifies protection + merge status at the
# moment of deletion, so nothing can drop a live branch
function deleteBranch () {
  local branch="$1" main_branch="$2" current

  if isProtected "$branch" "$main_branch"; then
    echo "  SKIP (protected):  $branch"
    return 1
  fi

  current=$(git symbolic-ref --short -q HEAD)
  if [[ -n "$current" && "$branch" == "$current" ]]; then
    echo "  SKIP (checked out): $branch"
    return 1
  fi

  if ! isMerged "$main_branch" "$branch"; then
    echo "  SKIP (not merged into $main_branch): $branch"
    return 1
  fi

  git branch -D "$branch"
}

# findOrphanedBranches <repo-path> <integration-branch>
function findOrphanedBranches () {
  local REPO="$1"
  local main_branch="$2"
  local branch current deleted=0 ans
  local -a orphaned=() active=()

  if [[ -z "$main_branch" ]]; then
    echo "!! usage: findOrphanedBranches <repo-path> <integration-branch>"
    return 1
  fi

  cd "$REPO" || { echo "!! cannot cd to $REPO -- skipping"; return 1; }

  if ! git rev-parse --verify --quiet "$main_branch" >/dev/null 2>&1; then
    echo "!! no '$main_branch' branch here -- skipping repo (nothing is treated as orphaned)"
    return 1
  fi

  current=$(git symbolic-ref --short -q HEAD)

  # for-each-ref instead of parsing `git branch` output: no '*' marker, no leading
  # whitespace, no chance of picking up detached-HEAD lines
  while IFS= read -r branch
  do
    [[ -z "$branch" ]] && continue
    isProtected "$branch" "$main_branch" && continue
    [[ -n "$current" && "$branch" == "$current" ]] && continue

    if isMerged "$main_branch" "$branch"; then
      orphaned+=("$branch")
    else
      active+=("$branch")
    fi
  done < <(git for-each-ref --format='%(refname:short)' refs/heads/)

  echo "-- NOT merged into $main_branch (${#active[@]}) -- keeping --"
  if [[ ${#active[@]} -eq 0 ]]; then
    echo "  (none)"
  else
    printf '  %s\n' "${active[@]}"
  fi
  echo

  echo "-- ORPHANED, merged into $main_branch (${#orphaned[@]}) -- deletable --"
  if [[ ${#orphaned[@]} -eq 0 ]]; then
    echo "  (none)"
    echo
    echo "nothing to delete"
    return 0
  fi
  printf '  %s\n' "${orphaned[@]}"
  echo

  echo "Delete ALL ${#orphaned[@]} orphaned branches?"
  select ans in Yes No
  do
    case $ans in
      Yes)
        for branch in "${orphaned[@]}"
        do
          deleteBranch "$branch" "$main_branch" && deleted=$((deleted + 1))
        done
        break
        ;;
      *)
        # anything other than an explicit Yes (No, a typo, an invalid number,
        # ctrl-D) deletes nothing at all
        echo "keeping everything"
        break
        ;;
    esac
  done

  echo
  echo "deleted $deleted of ${#orphaned[@]} orphaned branches"
}

# operate on the repo this script lives in, not on the caller's cwd
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd) || exit 1
REPO_ROOT=$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)
if [[ -z "$REPO_ROOT" ]]; then
  echo "!! $SCRIPT_DIR is not inside a git repo"
  exit 1
fi

echo ================================================
echo "$REPO_ROOT"
echo
findOrphanedBranches "$REPO_ROOT" develop

echo
read -r -p 'press ENTER to exit' _
echo
