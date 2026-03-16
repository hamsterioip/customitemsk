package com.example;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the structural integrity of all recipe JSON files without requiring
 * a Minecraft runtime. Tests run against files in src/main/resources at project root.
 */
class RecipeJsonTest {

    private static final Path RECIPE_DIR =
            Paths.get("src/main/resources/data/customitemsk/recipe");

    private List<Path> recipeFiles() throws IOException {
        return Files.walk(RECIPE_DIR)
                .filter(p -> p.toString().endsWith(".json"))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Directory / file presence
    // -------------------------------------------------------------------------

    @Test
    void recipeDirectoryExists() {
        assertTrue(Files.isDirectory(RECIPE_DIR),
                "Recipe directory not found: " + RECIPE_DIR.toAbsolutePath());
    }

    @Test
    void atLeastOneRecipeFilePresent() throws IOException {
        assertFalse(recipeFiles().isEmpty(),
                "No .json files found under " + RECIPE_DIR);
    }

    // -------------------------------------------------------------------------
    // JSON structure
    // -------------------------------------------------------------------------

    @Test
    void allRecipeFiles_areNonEmpty() throws IOException {
        for (Path file : recipeFiles()) {
            String content = Files.readString(file);
            assertFalse(content.isBlank(),
                    file.getFileName() + " is empty");
        }
    }

    @Test
    void allRecipeFiles_areJsonObjects() throws IOException {
        for (Path file : recipeFiles()) {
            String trimmed = Files.readString(file).strip();
            String name = file.getFileName().toString();
            assertTrue(trimmed.startsWith("{"),
                    name + ": expected JSON object (must start with '{')");
            assertTrue(trimmed.endsWith("}"),
                    name + ": expected JSON object (must end with '}')");
        }
    }

    @Test
    void allRecipeFiles_haveTypeField() throws IOException {
        for (Path file : recipeFiles()) {
            assertTrue(Files.readString(file).contains("\"type\""),
                    file.getFileName() + ": missing required 'type' field");
        }
    }

    @Test
    void allRecipeFiles_haveNonEmptyResultBlock() throws IOException {
        for (Path file : recipeFiles()) {
            String content = Files.readString(file);
            String name = file.getFileName().toString();
            if (content.contains("\"result\"")) {
                assertFalse(content.contains("\"result\": {}"),
                        name + ": result block is empty");
                assertFalse(content.contains("\"result\":{}"),
                        name + ": result block is empty");
            }
        }
    }

    @Test
    void allRecipeFiles_referenceCustomItemsKNamespace() throws IOException {
        // Every recipe should either be of a known vanilla type or produce a customitemsk item
        for (Path file : recipeFiles()) {
            String content = Files.readString(file);
            // At minimum the type or result must reference customitemsk or a known MC namespace
            boolean hasNamespace = content.contains("customitemsk:")
                    || content.contains("minecraft:")
                    || content.contains("fabricmc:");
            assertTrue(hasNamespace,
                    file.getFileName() + ": no recognised item namespace found");
        }
    }

    @Test
    void noRecipeFiles_haveMismatchedBraces() throws IOException {
        for (Path file : recipeFiles()) {
            String content = Files.readString(file);
            String name = file.getFileName().toString();
            long opens  = content.chars().filter(c -> c == '{').count();
            long closes = content.chars().filter(c -> c == '}').count();
            assertEquals(opens, closes,
                    name + ": mismatched braces (" + opens + " opening vs " + closes + " closing)");
        }
    }

    @Test
    void noRecipeFiles_haveMismatchedBrackets() throws IOException {
        for (Path file : recipeFiles()) {
            String content = Files.readString(file);
            String name = file.getFileName().toString();
            long opens  = content.chars().filter(c -> c == '[').count();
            long closes = content.chars().filter(c -> c == ']').count();
            assertEquals(opens, closes,
                    name + ": mismatched brackets (" + opens + " opening vs " + closes + " closing)");
        }
    }
}
