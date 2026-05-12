#!/bin/bash

# Kustomize Build Validation Script
# This script validates all Kustomize overlays for the Smart Hiring Assistant project

set -e

PROJECT_ROOT="/Users/vinodputhenmadhom/Downloads/JavaProjects/SmartHiringAssistant"
OVERLAYS=("staging" "prod")

echo "=================================================="
echo "Kustomize Overlay Validation"
echo "=================================================="
echo ""

validate_overlay() {
    local overlay=$1
    local overlay_path="${PROJECT_ROOT}/infrastructure/kubernetes/overlays/${overlay}"

    echo "📋 Testing overlay: ${overlay}"
    echo "   Path: ${overlay_path}"

    if [ ! -d "${overlay_path}" ]; then
        echo "❌ ERROR: Overlay directory not found: ${overlay_path}"
        return 1
    fi

    echo "   Building manifests..."
    local output
    local line_count

    output=$(cd "${PROJECT_ROOT}" && kubectl kustomize "${overlay_path}" 2>&1)
    line_count=$(echo "${output}" | grep -v "^#" | wc -l)

    # Check for errors (excluding deprecation warnings which start with #)
    if echo "${output}" | grep -q "^error:"; then
        echo "❌ BUILD FAILED"
        echo "${output}" | grep "^error:"
        return 1
    fi

    # Check for deprecation warning
    if echo "${output}" | grep -q "Warning:.*patchesStrategicMerge.*deprecated"; then
        echo "⚠️  Deprecation warning detected (acceptable, can be migrated later)"
    fi

    # Verify key resources are present
    local checks=(
        "kind: Namespace"
        "kind: ConfigMap"
        "kind: Deployment"
        "kind: Service"
        "kind: ServiceMonitor"
    )

    for check in "${checks[@]}"; do
        if echo "${output}" | grep -q "${check}"; then
            echo "   ✅ ${check} found"
        else
            echo "   ⚠️  ${check} not found"
        fi
    done

    # Check for init containers (PostgreSQL-dependent services)
    if echo "${output}" | grep -q "db-migrate"; then
        echo "   ✅ Init containers for database migration found"
    fi

    # Check for HPA in prod
    if [ "${overlay}" == "prod" ]; then
        if echo "${output}" | grep -q "kind: HorizontalPodAutoscaler"; then
            local hpa_count=$(echo "${output}" | grep -c "kind: HorizontalPodAutoscaler")
            echo "   ✅ ${hpa_count} HorizontalPodAutoscaler resources found"
        else
            echo "   ❌ No HPA resources found in prod overlay"
            return 1
        fi
    fi

    echo "   📊 Generated manifest lines: ${line_count}"
    echo "✅ ${overlay} overlay: VALID"
    echo ""

    return 0
}

# Run validation for all overlays
success=0
failed=0

for overlay in "${OVERLAYS[@]}"; do
    if validate_overlay "${overlay}"; then
        ((success++))
    else
        ((failed++))
    fi
done

echo "=================================================="
echo "Validation Summary"
echo "=================================================="
echo "✅ Successful: ${success}/${#OVERLAYS[@]}"
echo "❌ Failed: ${failed}/${#OVERLAYS[@]}"
echo ""

if [ ${failed} -eq 0 ]; then
    echo "🎉 All overlays are valid!"
    echo ""
    echo "Next steps:"
    echo "1. Deploy to staging: kubectl apply -k infrastructure/kubernetes/overlays/staging --validate=false"
    echo "2. Deploy to prod: kubectl apply -k infrastructure/kubernetes/overlays/prod --validate=false"
    exit 0
else
    echo "❌ Some overlays failed validation"
    exit 1
fi

