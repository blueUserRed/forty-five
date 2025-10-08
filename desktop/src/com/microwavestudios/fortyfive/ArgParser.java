package com.microwavestudios.fortyfive;

import com.microwavestudios.fortyfive.oven.BakeTask;
import com.microwavestudios.fortyfive.oven.DropShadowBakeTask;

import java.util.ArrayList;

public class ArgParser {

    private final String[] args;

    private int current = 0;
    private ArrayList<BakeTask> parsedBakeTasks = new ArrayList<>();
    private boolean isMapEditor = false;
    private String providedMapEditorPath = null;

    public ArgParser(String[] args) {
        this.args = args;
    }

    public FortyFive.AppArguments parse() {
        current = 0;
        parsedBakeTasks.clear();
        while (current < args.length) {
            String curArg = args[current];
            switch (curArg) {
                case "-bake" -> {
                    current++;
                    parseBake();
                }
                case "-mapEditor" -> {
                    current++;
                    parseMapEditor();
                }
                default -> throw new ArgumentParseException("unknown argument: " + curArg);
            }
        }
        return new FortyFive.AppArguments(
            !parsedBakeTasks.isEmpty(),
            parsedBakeTasks,
            isMapEditor,
            providedMapEditorPath
        );
    }

    private void parseMapEditor() {
        isMapEditor = true;
        if (current >= args.length) return;
        String curArg = args[current];
        if (curArg.startsWith("-")) return;
        current++;
        providedMapEditorPath = curArg;
    }

    private void parseBake() {
        LOOP:
        while (true) {
            if (current >= args.length) break;
            String curArg = args[current];

            switch (curArg) {

                case "dropShadows" -> {
                    current++;
                    boolean isIncremental = current < args.length && args[current].equals("incremental");
                    boolean isSpecific = !isIncremental &&
                            current < args.length &&
                            args[current].startsWith("\"") &&
                            args[current].endsWith("\"");
                    String specific = null;
                    if (isSpecific) specific = args[current];
                    if (isSpecific || isIncremental) current++;
                    BakeTask task = new DropShadowBakeTask(isIncremental, specific);
                    parsedBakeTasks.add(task);
                }

                default -> {
                    break LOOP;
                }
            }

            current++;
        }
    }

    public static class ArgumentParseException extends RuntimeException {

        public ArgumentParseException(String message) {
            super(message);
        }
    }
}
