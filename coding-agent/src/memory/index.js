const fs = require("fs");

// 记忆系统：管理短期记忆（近期观察）与长期记忆（跨会话持久化的事实）
class Memory {
    constructor(config) {
        this.config = config || {};
        this.shortTermLimit = this.config.shortTermLimit || 50;
        // 短期记忆：本次会话内有效的观察/中间结果
        this.shortTerm = [];
        // 长期记忆：需跨会话保留的关键事实
        this.longTerm = [];
        this.longTermFile = this.config.longTermFile;
        this.load();
    }

    // 添加一条短期记忆，超出上限自动淘汰最早条目
    addShortTerm(text) {
        if (!text) {
            return;
        }
        this.shortTerm.push({ text: text, time: Date.now() });
        if (this.shortTerm.length > this.shortTermLimit) {
            this.shortTerm.shift();
        }
    }

    // 添加一条长期记忆并立即持久化
    addLongTerm(text) {
        if (!text) {
            return;
        }
        this.longTerm.push({ text: text, time: Date.now() });
        this.save();
    }

    // 获取短期记忆文本列表
    getShortTerm() {
        return this.shortTerm.map(function (m) {
            return m.text;
        });
    }

    // 获取长期记忆文本列表
    getLongTerm() {
        return this.longTerm.map(function (m) {
            return m.text;
        });
    }

    // 从文件加载长期记忆（失败时静默置空，不影响运行）
    load() {
        if (!this.longTermFile) {
            return;
        }
        try {
            if (fs.existsSync(this.longTermFile)) {
                const data = fs.readFileSync(this.longTermFile, "utf-8");
                const parsed = JSON.parse(data);
                this.longTerm = Array.isArray(parsed) ? parsed : [];
            }
        } catch (e) {
            this.longTerm = [];
        }
    }

    // 将长期记忆写入文件
    save() {
        if (!this.longTermFile) {
            return;
        }
        try {
            fs.writeFileSync(
                this.longTermFile,
                JSON.stringify(this.longTerm, null, 2),
                "utf-8"
            );
        } catch (e) {
            // 持久化失败不中断主流程
        }
    }

    // 清空短期记忆
    clearShortTerm() {
        this.shortTerm = [];
    }

    // 清空全部记忆（含持久化的长期记忆）
    clearAll() {
        this.shortTerm = [];
        this.longTerm = [];
        this.save();
    }
}

module.exports = Memory;
