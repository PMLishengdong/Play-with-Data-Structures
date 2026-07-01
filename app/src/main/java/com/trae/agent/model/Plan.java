package com.trae.agent.model;

import java.util.List;

/**
 * A plan consisting of an ordered list of tasks the agent will execute.
 */
public class Plan {
    private String goal;
    private List<Task> tasks;
    private int currentTaskIndex;

    public Plan() {}

    public Plan(String goal, List<Task> tasks) {
        this.goal = goal;
        this.tasks = tasks;
        this.currentTaskIndex = 0;
    }

    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }

    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }

    public int getCurrentTaskIndex() { return currentTaskIndex; }
    public void setCurrentTaskIndex(int index) { this.currentTaskIndex = index; }

    /** Returns the next pending task, or null if all done. */
    public Task getNextTask() {
        if (currentTaskIndex < tasks.size()) {
            return tasks.get(currentTaskIndex);
        }
        return null;
    }

    /** Advance to the next task. */
    public void advance() {
        currentTaskIndex++;
    }

    public boolean isComplete() {
        return currentTaskIndex >= tasks.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Plan: ").append(goal).append("\n");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            sb.append(i == currentTaskIndex ? "  → " : "    ")
              .append(t).append("\n");
        }
        return sb.toString();
    }
}