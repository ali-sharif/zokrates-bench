package org.aion.tetryon;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class IntegrationTest {


    private static void call(List<String> command, File directory) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command);
        pb.directory(directory);

        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

            int exit = process.waitFor();
            if (exit == 0) {
                System.out.println(output);
            } else {
                System.out.println("processes exited with non-zero return value.");
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @BeforeClass
    public static void before() throws IOException, InterruptedException {
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void preimageTest() throws IOException {

        String ps = "g16";
        File testFolder = folder.newFolder("preimage");
        File codeFile = folder.newFile("preimage/root.code");

        FileUtils.writeStringToFile(codeFile, "hello world", (String) null, false);

        //noinspection ConstantConditions
        String code = FileUtils.readFileToString(
                new File(getClass().getClassLoader().getResource("preimage.zok").getFile()), // from src/test/resources folder
                (String) null);

        FileUtils.writeStringToFile(codeFile, code, (String) null, false);

        // sanity check
        final String s = FileUtils.readFileToString(codeFile, (String) null);
        Assert.assertEquals(code, s);

        File projectDir = new File(".");

        String zokrates = projectDir.getCanonicalPath() + "/../zokrates/target/debug/zokrates";

        List<String> compile = Arrays.asList(zokrates, "compile", "-i", codeFile.getCanonicalPath());
        call(compile, testFolder);

        List<String> setup = Arrays.asList(zokrates, "setup", "--proving-scheme", ps);
        call(setup, testFolder);

        List<String> exportVerifier = Arrays.asList(zokrates, "export-avm-verifier", "--proving-scheme", ps);
        call(exportVerifier, testFolder);


        List<String> computeWitness = Arrays.asList(zokrates, "compute-witness", "-a", "337", "113569");
        call(computeWitness, testFolder);

        List<String> generateProof = Arrays.asList(zokrates, "generate-proof", "--proving-scheme", ps);
        call(generateProof, testFolder);

        System.out.println("hello world");
    }
}
