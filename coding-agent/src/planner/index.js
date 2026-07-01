// 任务规划器：调用 LLM 将一个高层目标拆解为具体、可执行的步骤列表
class Planner {
    constructor(llm) {
        this.llm = llm;
    }

    // 调用 LLM 生成步骤，返回字符串数组
    async plan(goal) {
        const prompt = [
            {
                role: "system",
                content: "你是一个任务规划助手。请将用户目标拆解为具体、可执行的步骤，并以 JSON 字符串数组形式返回，例如 [\"步骤1\",\"步骤2\"]。只返回 JSON，不要任何其他内容。"
            },
            {
                role: "user",
                content: `目标: ${goal}`
            }
        ];
        const resp = await this.llm.chat(prompt, {});
        const text = resp.content || "";
        return this.parseSteps(text);
    }

    // 从 LLM 输出中解析步骤数组，解析失败时回退为单步
    parseSteps(text) {
        if (!text) {
            return [];
        }
        // 提取首个 JSON 数组片段
        const match = text.match(/\[[\s\S]*\]/);
        const jsonStr = match ? match[0] : text;
        try {
            const arr = JSON.parse(jsonStr);
            if (Array.isArray(arr)) {
                return arr.filter(function (s) {
                    return typeof s === "string";
                });
            }
        } catch (e) {
            // 解析失败，回退
        }
        return [text];
    }
}

module.exports = Planner;
