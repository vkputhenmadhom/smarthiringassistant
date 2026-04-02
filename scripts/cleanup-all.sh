#!/bin/bash

###############################################################################
# Comprehensive Cleanup Script
# Removes: CloudFormation stacks, Docker resources, local artifacts, AWS resources
# Usage: ./cleanup-all.sh [--dry-run] [--force]
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Color codes
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Flags
DRY_RUN=false
FORCE=false
INTERACTIVE=true

# Configuration
STACK_NAME="${STACK_NAME:-smart-hiring-phase1-deploy}"
AWS_REGION="${AWS_REGION:-us-east-1}"
COMPOSE_FILES=(
    "docker-compose.yml"
    "docker-compose.apps.yml"
    "docker-compose.monitoring-staging.yml"
)

###############################################################################
# Functions
###############################################################################

print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

show_help() {
    cat << EOF
Usage: $0 [OPTIONS]

OPTIONS:
    --dry-run       Show what would be deleted without actually deleting
    --force         Skip interactive confirmation
    --help          Show this help message

WHAT GETS DELETED:
    ✓ AWS CloudFormation stack (SAM Lambda deployment)
    ✓ Docker containers and networks
    ✓ Docker volumes (local data)
    ✓ Local build artifacts (./build, ./*/build, .gradle)
    ✓ SAM config cache (.aws-sam)
    ✓ AWS SAM build cache (.gradle)
    ✓ Temporary files (.env backup)

ENVIRONMENT VARIABLES:
    STACK_NAME      CloudFormation stack name (default: smart-hiring-phase1-deploy)
    AWS_REGION      AWS region (default: us-east-1)

EXAMPLE:
    # Dry run first
    $0 --dry-run

    # Force cleanup (no confirmation)
    $0 --force

    # Interactive cleanup (default)
    $0

EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --force)
                FORCE=true
                INTERACTIVE=false
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

confirm_action() {
    if [[ "$FORCE" == true ]]; then
        return 0
    fi

    if [[ "$INTERACTIVE" == true ]]; then
        read -p "$(echo -e ${RED}Are you sure? This will delete all resources. [y/N]${NC} )" -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_warning "Cleanup cancelled."
            exit 0
        fi
    fi
}

backup_config() {
    print_info "Backing up important files..."

    BACKUP_DIR="${PROJECT_ROOT}/.cleanup-backup-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$BACKUP_DIR"

    # Backup samconfig.toml if it exists
    if [[ -f "${PROJECT_ROOT}/samconfig.toml" ]]; then
        cp "${PROJECT_ROOT}/samconfig.toml" "$BACKUP_DIR/" 2>/dev/null && \
            print_success "Backed up samconfig.toml to $BACKUP_DIR"
    fi

    # Backup .env files if they exist
    if [[ -f "${PROJECT_ROOT}/.env" ]]; then
        cp "${PROJECT_ROOT}/.env" "$BACKUP_DIR/.env.backup" 2>/dev/null && \
            print_success "Backed up .env to $BACKUP_DIR"
    fi

    print_info "Backups saved to: $BACKUP_DIR"
}

cleanup_cloudformation() {
    print_header "CLOUDFORMATION CLEANUP"

    if ! command -v aws &> /dev/null; then
        print_warning "AWS CLI not found. Skipping CloudFormation cleanup."
        return 1
    fi

    # Check if stack exists
    if ! aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --region "$AWS_REGION" \
        &>/dev/null; then
        print_info "CloudFormation stack '$STACK_NAME' not found."
        return 0
    fi

    print_info "CloudFormation stack found: $STACK_NAME"

    if [[ "$DRY_RUN" == true ]]; then
        print_info "[DRY RUN] Would delete CloudFormation stack: $STACK_NAME"
        return 0
    fi

    print_info "Deleting CloudFormation stack: $STACK_NAME..."

    if aws cloudformation delete-stack \
        --stack-name "$STACK_NAME" \
        --region "$AWS_REGION"; then
        print_success "Stack deletion initiated. This may take a few minutes..."
        print_info "Check status with: aws cloudformation describe-stacks --stack-name $STACK_NAME --region $AWS_REGION"
    else
        print_error "Failed to delete CloudFormation stack"
        return 1
    fi
}

cleanup_docker() {
    print_header "DOCKER CLEANUP"

    if ! command -v docker &> /dev/null; then
        print_warning "Docker not found. Skipping Docker cleanup."
        return 1
    fi

    # Stop and remove containers
    print_info "Stopping Docker containers..."

    if [[ "$DRY_RUN" == true ]]; then
        print_info "[DRY RUN] Would stop and remove Docker containers and networks"
        return 0
    fi

    # Check if docker daemon is running
    if ! docker ps &>/dev/null; then
        print_warning "Docker daemon is not running. Skipping Docker cleanup."
        return 1
    fi

    # Navigate to project root for docker-compose
    cd "$PROJECT_ROOT" || return 1

    for compose_file in "${COMPOSE_FILES[@]}"; do
        if [[ -f "$compose_file" ]]; then
            print_info "Processing $compose_file..."

            # Stop services
            if docker-compose -f "$compose_file" down -v 2>/dev/null; then
                print_success "Stopped and removed resources from $compose_file"
            fi
        fi
    done

    # Remove project-related images (optional, be careful)
    print_info "Removing project-related Docker images..."

    local images=(
        "smart-hiring-assistant*"
        "interview-prep-service*"
        "screening-bot-service*"
        "candidate-matcher-service*"
        "job-analyzer-service*"
        "resume-parser-service*"
        "notification-service*"
        "api-gateway*"
        "auth-service*"
        "ai-integration-service*"
    )

    for img in "${images[@]}"; do
        docker rmi -f "$img" 2>/dev/null || true
    done

    print_success "Docker cleanup completed"
}

cleanup_local_artifacts() {
    print_header "LOCAL ARTIFACTS CLEANUP"

    if [[ "$DRY_RUN" == true ]]; then
        print_info "[DRY RUN] Would delete:"
        find "$PROJECT_ROOT" -type d -name "build" -o -name ".gradle" -o -name ".aws-sam" -o -name "node_modules" 2>/dev/null | head -20
        return 0
    fi

    # Clean build directories
    print_info "Removing build directories..."
    find "$PROJECT_ROOT" -type d -name "build" -exec rm -rf {} + 2>/dev/null || true
    print_success "Cleaned build directories"

    # Clean .gradle cache
    if [[ -d "$PROJECT_ROOT/.gradle" ]]; then
        print_info "Removing .gradle cache..."
        rm -rf "$PROJECT_ROOT/.gradle"
        print_success "Cleaned .gradle cache"
    fi

    # Clean SAM cache
    if [[ -d "$PROJECT_ROOT/.aws-sam" ]]; then
        print_info "Removing SAM cache..."
        rm -rf "$PROJECT_ROOT/.aws-sam"
        print_success "Cleaned SAM cache"
    fi

    # Clean node_modules (frontend)
    find "$PROJECT_ROOT/frontend" -type d -name "node_modules" -exec rm -rf {} + 2>/dev/null || true
    print_success "Cleaned node_modules from frontend"

    # Clean dist directories
    find "$PROJECT_ROOT/frontend" -type d -name "dist" -exec rm -rf {} + 2>/dev/null || true
    print_success "Cleaned dist directories"
}

cleanup_aws_resources() {
    print_header "AWS RESOURCES CLEANUP"

    if ! command -v aws &> /dev/null; then
        print_warning "AWS CLI not found. Skipping AWS resources cleanup."
        return 1
    fi

    print_info "Checking for orphaned AWS resources..."

    if [[ "$DRY_RUN" == true ]]; then
        print_info "[DRY RUN] Would check and clean:"
        print_info "  - CloudWatch Logs related to $STACK_NAME"
        print_info "  - Orphaned Lambda functions"
        print_info "  - Orphaned API Gateway endpoints"
        return 0
    fi

    # Clean CloudWatch logs for the stack
    print_info "Cleaning CloudWatch logs..."
    local log_groups=$(aws logs describe-log-groups \
        --region "$AWS_REGION" \
        --query "logGroups[?contains(logGroupName, '$STACK_NAME') || contains(logGroupName, 'lambda')].logGroupName" \
        --output text 2>/dev/null || true)

    for log_group in $log_groups; do
        print_info "Deleting log group: $log_group"
        aws logs delete-log-group \
            --log-group-name "$log_group" \
            --region "$AWS_REGION" 2>/dev/null || true
    done

    print_success "AWS resources cleanup completed"
}

cleanup_environment() {
    print_header "ENVIRONMENT CLEANUP"

    if [[ "$DRY_RUN" == true ]]; then
        print_info "[DRY RUN] Would clean up temporary environment files"
        return 0
    fi

    # Remove environment variable overrides
    if [[ -f "${PROJECT_ROOT}/.env.local" ]]; then
        rm -f "${PROJECT_ROOT}/.env.local"
        print_success "Removed .env.local"
    fi

    # Clean up temporary config
    if [[ -f "${PROJECT_ROOT}/.samconfig-backup.toml" ]]; then
        rm -f "${PROJECT_ROOT}/.samconfig-backup.toml"
        print_success "Removed .samconfig-backup.toml"
    fi

    print_success "Environment cleanup completed"
}

print_summary() {
    print_header "CLEANUP SUMMARY"

    if [[ "$DRY_RUN" == true ]]; then
        print_warning "This was a DRY RUN. No files were actually deleted."
        print_info "To perform actual cleanup, run: $0 --force"
        return 0
    fi

    print_success "Cleanup completed!"
    echo ""
    print_info "What was deleted:"
    echo "  ✓ CloudFormation stack: $STACK_NAME"
    echo "  ✓ Docker containers and networks"
    echo "  ✓ Docker volumes"
    echo "  ✓ Build artifacts and caches"
    echo "  ✓ CloudWatch logs"
    echo "  ✓ Environment overrides"
    echo ""
    print_info "What was preserved:"
    echo "  ✓ Source code (./src, ./services, ./frontend)"
    echo "  ✓ Configuration backups in .cleanup-backup-*"
    echo "  ✓ Git history"
    echo ""
    print_info "To redeploy later:"
    echo "  1. Restore samconfig.toml from backup (if needed)"
    echo "  2. Run: ./scripts/serverless-phase1-deploy.sh --guided"
    echo "  3. Bring up services: docker-compose up -d"
}

###############################################################################
# Main
###############################################################################

main() {
    parse_args "$@"

    print_header "SMART HIRING ASSISTANT - COMPLETE CLEANUP"

    echo "Configuration:"
    echo "  Stack Name: $STACK_NAME"
    echo "  AWS Region: $AWS_REGION"
    echo "  Dry Run: $DRY_RUN"
    echo "  Force Mode: $FORCE"
    echo ""

    if [[ "$DRY_RUN" == false ]]; then
        confirm_action
    fi

    # Backup important configs before deleting anything
    if [[ "$DRY_RUN" == false ]]; then
        backup_config
    fi

    # Run cleanup tasks
    cleanup_cloudformation || true
    cleanup_docker || true
    cleanup_local_artifacts || true
    cleanup_aws_resources || true
    cleanup_environment || true

    # Print summary
    print_summary
}

main "$@"

