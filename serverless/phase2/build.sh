#!/bin/bash
# Independent build script for AI Integration Lambda (Phase 2)
# This demonstrates the module can build independently without other services

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MODULE_DIR="$SCRIPT_DIR"

echo "🔨 Building AI Integration Lambda (Phase 2)"
echo "   Module: $MODULE_DIR"
echo ""

# Step 1: Build the module independently
echo "📦 Step 1: Building module with Gradle..."
cd "$MODULE_DIR"
gradle build || {
    echo "❌ Build failed"
    exit 1
}
echo "✅ Build succeeded"
echo ""

# Step 2: Package Lambda ZIP
echo "📦 Step 2: Packaging Lambda ZIP..."
gradle packageLambdaZip || {
    echo "❌ Packaging failed"
    exit 1
}
echo "✅ ZIP packaged: $MODULE_DIR/functions/ai-integration-lambda/build/function.zip"
echo ""

# Step 3: Validate SAM template
echo "📋 Step 3: Validating SAM template..."
if command -v sam &> /dev/null; then
    sam validate -t "$MODULE_DIR/template.yaml" || {
        echo "❌ SAM validation failed"
        exit 1
    }
    echo "✅ SAM template valid"
else
    echo "⚠️  SAM CLI not installed, skipping validation"
fi
echo ""

# Step 4: Report
echo "🎉 AI Integration Lambda Phase 2 build complete!"
echo ""
echo "📊 Build artifacts:"
ls -lh "$MODULE_DIR/functions/ai-integration-lambda/build/function.zip"
echo ""
echo "Next steps:"
echo "  1. Local test:  sam local invoke AiIntegrationFunction -t template.yaml -e events/ai-generate-request.json"
echo "  2. Deploy:      sam deploy --config-env default"
echo "  3. Test live:   curl https://<api-id>.execute-api.us-east-1.amazonaws.com/Prod/health"

