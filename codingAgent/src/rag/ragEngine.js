"use strict";

const config = require("../config");

/**
 * RAG 引擎：基于向量嵌入的文档检索。
 * 负责文档分块、生成 embedding、存储 chunk 并根据查询返回最相关的片段。
 */
class RagEngine {
    /**
     * 构造 RAG 引擎。
     * @param {LlmClient} llmClient 具备 embed 能力的 LLM 客户端
     */
    constructor(llmClient) {
        this.llmClient = llmClient;
        this.chunks = [];
        this.chunkSize = config.rag.chunkSize;
        this.chunkOverlap = config.rag.chunkOverlap;
    }

    /**
     * 添加一篇文档：先分块，再为每个 chunk 生成向量，最后存入索引。
     * @param {string} content 文档全文
     * @param {Object} [metadata={}] 文档元数据
     */
    async addDocument(content, metadata) {
        if (!metadata) {
            metadata = {};
        }
        const pieces = this._splitText(content);
        if (pieces.length === 0) {
            return;
        }

        const embeddings = await this.llmClient.embed(pieces);
        for (let i = 0; i < pieces.length; i += 1) {
            this.chunks.push({
                content: pieces[i],
                metadata: metadata,
                embedding: embeddings[i]
            });
        }
    }

    /**
     * 根据查询文本检索最相关的 k 个文档片段。
     * 若 embedding 服务不可用，则退化为关键词匹配。
     * @param {string} query 查询文本
     * @param {number} [topK] 返回条数，默认取 config.rag.topK
     * @returns {Promise<Array<{content:string, metadata:Object, score:number}>>}
     */
    async retrieve(query, topK) {
        if (!topK) {
            topK = config.rag.topK;
        }
        if (this.chunks.length === 0) {
            return [];
        }

        let queryEmbedding = null;
        try {
            const embeddings = await this.llmClient.embed([query]);
            queryEmbedding = embeddings[0];
        } catch (error) {
            console.warn("Embedding 失败，启用关键词回退检索:", error.message);
            return this._keywordFallback(query, topK);
        }

        const results = [];
        for (let i = 0; i < this.chunks.length; i += 1) {
            const chunk = this.chunks[i];
            const score = this._cosineSimilarity(queryEmbedding, chunk.embedding);
            results.push({
                content: chunk.content,
                metadata: chunk.metadata,
                score: score
            });
        }

        results.sort(function (a, b) {
            return b.score - a.score;
        });
        return results.slice(0, topK);
    }

    /**
     * 简单的滑动窗口文本分块。
     * @param {string} text
     * @returns {string[]}
     */
    _splitText(text) {
        const result = [];
        const step = this.chunkSize - this.chunkOverlap;
        for (let start = 0; start < text.length; start += step) {
            const end = Math.min(start + this.chunkSize, text.length);
            result.push(text.substring(start, end));
            if (end === text.length) {
                break;
            }
        }
        return result;
    }

    /**
     * 计算两个向量间的余弦相似度。
     * @param {number[]} a
     * @param {number[]} b
     * @returns {number}
     */
    _cosineSimilarity(a, b) {
        if (!a || !b || a.length !== b.length) {
            return 0;
        }
        let dot = 0;
        let normA = 0;
        let normB = 0;
        for (let i = 0; i < a.length; i += 1) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA === 0 || normB === 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 关键词回退检索：按查询词在 chunk 中的命中次数排序。
     * @param {string} query
     * @param {number} topK
     * @returns {Array<{content:string, metadata:Object, score:number}>}
     */
    _keywordFallback(query, topK) {
        const words = query.toLowerCase().split(/\s+/).filter(function (w) {
            return w.length > 1;
        });
        const results = [];
        for (let i = 0; i < this.chunks.length; i += 1) {
            const chunk = this.chunks[i];
            const lowerContent = chunk.content.toLowerCase();
            let score = 0;
            for (let j = 0; j < words.length; j += 1) {
                const regex = new RegExp(words[j], "g");
                const matches = lowerContent.match(regex);
                if (matches) {
                    score += matches.length;
                }
            }
            if (score > 0) {
                results.push({
                    content: chunk.content,
                    metadata: chunk.metadata,
                    score: score
                });
            }
        }
        results.sort(function (a, b) {
            return b.score - a.score;
        });
        return results.slice(0, topK);
    }

    /**
     * 获取当前索引中的 chunk 总数。
     * @returns {number}
     */
    count() {
        return this.chunks.length;
    }
}

module.exports = RagEngine;
