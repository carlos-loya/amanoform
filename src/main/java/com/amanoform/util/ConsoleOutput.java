package com.amanoform.util;

/**
 * Enterprise-grade console output formatting utility.
 *
 * <p>Provides ANSI color code management for terminal output rendering.
 * In Python, the Rich library handles this in one import. In Java,
 * we write our own utility class with static methods and private
 * constructors, as is tradition.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public final class ConsoleOutput {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String RED = "\033[31m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";

    /** Prevent instantiation of utility class. */
    private ConsoleOutput() {
        throw new UnsupportedOperationException(
            "ConsoleOutput is a utility class and cannot be instantiated. "
            + "This is Java — we enforce design patterns at the constructor level."
        );
    }

    public static void printBold(String message) {
        System.out.println(BOLD + message + RESET);
    }

    public static void printGreen(String message) {
        System.out.println(GREEN + message + RESET);
    }

    public static void printRed(String message) {
        System.out.println(RED + message + RESET);
    }

    public static void printYellow(String message) {
        System.out.println(YELLOW + message + RESET);
    }

    public static void printBoldGreen(String message) {
        System.out.println(BOLD + GREEN + message + RESET);
    }

    public static void printBoldRed(String message) {
        System.out.println(BOLD + RED + message + RESET);
    }

    public static void print(String message) {
        System.out.println(message);
    }

    /**
     * Format an action line for plan/apply output.
     *
     * @param actionType one of "create", "update", "destroy"
     * @param resourceKey the resource identifier
     * @param suffix additional text to append
     */
    public static void printAction(String actionType, String resourceKey, String suffix) {
        String symbol;
        String label;

        switch (actionType) {
            case "create":
                symbol = GREEN + "+" + RESET;
                label = GREEN + "created" + RESET;
                break;
            case "update":
                symbol = YELLOW + "~" + RESET;
                label = YELLOW + "updated" + RESET;
                break;
            case "destroy":
                symbol = RED + "-" + RESET;
                label = RED + "destroyed" + RESET;
                break;
            default:
                symbol = "?";
                label = "unknown";
        }

        if (suffix != null && !suffix.isEmpty()) {
            System.out.println("  " + symbol + " " + resourceKey + " " + suffix);
        } else {
            System.out.println("  " + symbol + " " + resourceKey + " will be " + label);
        }
    }

    /**
     * Print a plan summary line.
     *
     * @param toAdd number of resources to create
     * @param toChange number of resources to update
     * @param toDestroy number of resources to destroy
     */
    public static void printPlanSummary(int toAdd, int toChange, int toDestroy) {
        System.out.println();
        printBold("Plan: " + toAdd + " to add, " + toChange + " to change, "
                + toDestroy + " to destroy.");
        System.out.println();
    }
}
