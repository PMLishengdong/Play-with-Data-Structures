/**
 * React Agent - 扩展 CodingAgent 的领域特定 Agent 示例
 * 展示了如何通过继承和自定义工具来创建特定领域的 Agent
 */

const { CodingAgent } = require('../src/agent');
const { BaseTool } = require('../src/tools');

// ---- 自定义 React 专用工具 ----

/**
 * 分析 React 组件依赖的工具
 */
class AnalyzeComponentTool extends BaseTool {
  constructor() {
    super({
      name: 'analyze_component',
      description: '分析 React 组件的 props、state、hooks 和子组件依赖',
      parameters: {
        type: 'object',
        properties: {
          file_path: {
            type: 'string',
            description: '组件文件的绝对路径',
          },
        },
        required: ['file_path'],
      },
    });
  }

  async execute(args) {
    const { file_path } = args;
    const fs = require('fs');

    if (!fs.existsSync(file_path)) {
      return `Error: File not found at "${file_path}"`;
    }

    const content = fs.readFileSync(file_path, 'utf-8');

    const analysis = {
      imports: [],
      hooks: [],
      props: [],
      components: [],
    };

    // 提取 import
    const importRegex = /import\s+(?:\{[^}]*\}|[^;]+)\s+from\s+['"]([^'"]+)['"]/g;
    let match;
    while ((match = importRegex.exec(content)) !== null) {
      analysis.imports.push(match[1]);
    }

    // 提取 hooks (useXxx)
    const hookRegex = /use[A-Z]\w+/g;
    while ((match = hookRegex.exec(content)) !== null) {
      if (!analysis.hooks.includes(match[0])) {
        analysis.hooks.push(match[0]);
      }
    }

    // 提取 props interface/type
    const propRegex = /(?:interface|type)\s+(\w+(?:Props|Prop))\s*(?:extends\s+\w+\s*)?[={]/g;
    while ((match = propRegex.exec(content)) !== null) {
      analysis.props.push(match[1]);
    }

    // 提取子组件使用
    const jsxRegex = /<([A-Z]\w+)/g;
    while ((match = jsxRegex.exec(content)) !== null) {
      if (match[1] !== 'div' && match[1] !== 'span' && !analysis.components.includes(match[1])) {
        analysis.components.push(match[1]);
      }
    }

    let result = `## Component Analysis: ${require('path').basename(file_path)}\n`;
    result += `- Imports: ${analysis.imports.length} (${analysis.imports.join(', ')})\n`;
    result += `- Hooks: ${analysis.hooks.join(', ') || 'none'}\n`;
    result += `- Props interface: ${analysis.props.join(', ') || 'none'}\n`;
    result += `- Sub-components: ${analysis.components.join(', ') || 'none'}\n`;

    return result;
  }
}

/**
 * 生成 React 组件代码的工具
 */
class GenerateReactComponentTool extends BaseTool {
  constructor() {
    super({
      name: 'generate_react_component',
      description: '根据描述生成 React 组件代码（TypeScript）',
      parameters: {
        type: 'object',
        properties: {
          name: {
            type: 'string',
            description: '组件名称（PascalCase）',
          },
          description: {
            type: 'string',
            description: '组件的功能描述',
          },
          props: {
            type: 'string',
            description: 'props 的 JSON 描述',
          },
          output_path: {
            type: 'string',
            description: '输出文件路径',
          },
        },
        required: ['name', 'description', 'output_path'],
      },
    });
  }

  async execute(args) {
    const { name, description, props, output_path } = args;
    const fs = require('fs');
    const path = require('path');

    let propsInterface = '';
    let propDefinitions = '';
    let propUsage = '';

    if (props) {
      try {
        const parsedProps = typeof props === 'string' ? JSON.parse(props) : props;
        const entries = Object.entries(parsedProps);
        if (entries.length > 0) {
          propsInterface = `\ninterface ${name}Props {\n${entries.map(([k, v]) => `  ${k}${v.required !== false ? '' : '?'}: ${v.type || 'string'};`).join('\n')}\n}`;
          propDefinitions = `{ ${Object.keys(parsedProps).join(', ')} }: ${name}Props`;
          propUsage = entries.map(([k]) => `      <div>${k}: {${k}}</div>`).join('\n');
        }
      } catch {
        propDefinitions = `props: any`;
      }
    }

    const componentCode = `import React from 'react';
${propsInterface || ''}

const ${name}: React.FC${props ? `<${name}Props>` : ''} = (${propDefinitions || 'props'}) => {
  return (
    <div className="${name.toLowerCase()}">
      <h2>${name}</h2>
      <p>${description}</p>
      ${propUsage || '      {/* TODO: implement */}'}
    </div>
  );
};

export default ${name};
`;

    const dir = path.dirname(output_path);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(output_path, componentCode, 'utf-8');

    return `Generated React component "${name}" at "${output_path}"\n\n\`\`\`tsx\n${componentCode}\`\`\``;
  }
}

/**
 * ReactAgent - 专注于 React/前端开发的 Agent
 */
class ReactAgent extends CodingAgent {
  constructor(config = {}) {
    // 设置 React 专属系统提示
    const reactSystemPrompt = (config.systemPrompt || '') + `

## React Development Specialization
You are also a React/TypeScript expert. Follow these rules:
1. Use functional components with hooks (no class components)
2. Always use TypeScript interfaces for props
3. Keep components small and focused (single responsibility)
4. Use Tailwind CSS classes for styling
5. Extract reusable logic into custom hooks
6. Use React.memo() for performance-critical components

Available React-specific tools:
- analyze_component: Analyze existing React components
- generate_react_component: Generate new React components from descriptions
`;

    super({
      ...config,
      systemPrompt: reactSystemPrompt,
    });

    // 注册 React 专用工具
    this.registerTool(new AnalyzeComponentTool());
    this.registerTool(new GenerateReactComponentTool());
  }

  /**
   * 快速生成组件的便捷方法
   */
  async generateComponent(name, description, props, outputDir) {
    const outputPath = require('path').join(outputDir, `${name}.tsx`);
    return await this.toolRegistry.executeTool('generate_react_component', {
      name,
      description,
      props,
      output_path: outputPath,
    }, this._createAgentContext());
  }

  /**
   * 分析项目中所有组件的依赖关系
   */
  async analyzeProject(projectDir) {
    const glob = require('glob');
    const files = glob.sync('**/*.{tsx,jsx}', { cwd: projectDir, absolute: true });

    const results = [];
    for (const file of files.slice(0, 20)) { // 限制分析数量
      const result = await this.toolRegistry.executeTool('analyze_component', {
        file_path: file,
      }, this._createAgentContext());
      results.push(result);
    }

    return results.join('\n---\n');
  }
}

// ---- Demo ----

async function main() {
  const agent = new ReactAgent({
    workingDirectory: process.cwd(),
    verbose: true,
  });

  console.log('=== React Agent Demo ===\n');
  console.log('Available tools:', agent.toolRegistry.listTools().join(', '), '\n');

  // 演示 generateComponent 便捷方法
  const outputDir = '/tmp/react-components';
  const result = await agent.generateComponent(
    'UserProfile',
    'Displays user avatar, name, and bio information',
    JSON.stringify({
      username: { type: 'string', required: true },
      avatarUrl: { type: 'string', required: true },
      bio: { type: 'string', required: false },
      followers: { type: 'number', required: false },
    }),
    outputDir
  );
  console.log(result);

  // 分析组件
  console.log('\n--- Analyzing generated component ---');
  const analysis = await agent.toolRegistry.executeTool(
    'analyze_component',
    { file_path: `${outputDir}/UserProfile.tsx` },
    agent._createAgentContext()
  );
  console.log(analysis);
}

if (require.main === module) {
  main().catch(err => {
    console.error('React agent demo error:', err);
    process.exit(1);
  });
}

module.exports = { ReactAgent, AnalyzeComponentTool, GenerateReactComponentTool };