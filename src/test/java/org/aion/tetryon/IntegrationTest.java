package org.aion.tetryon;

import avm.Address;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import javax.tools.*;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.aion.tetryon.Verifier.Proof;

public class IntegrationTest {


    private static void call(List<String> command, File directory) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command);
        pb.directory(directory);

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
    }

    /**
     * This function builds a classpath from the passed Strings
     *
     * @param paths classpath elements
     * @return returns the complete classpath with wildcards expanded
     */
    private static String buildClassPath(String... paths) {
        StringBuilder sb = new StringBuilder();
        for (String path : paths) {
            if (path.endsWith("*")) {
                path = path.substring(0, path.length() - 1);
                File pathFile = new File(path);
                for (File file : pathFile.listFiles()) {
                    if (file.isFile() && file.getName().endsWith(".jar")) {
                        sb.append(path);
                        sb.append(file.getName());
                        sb.append(System.getProperty("path.separator"));
                    }
                }
            } else {
                sb.append(path);
                sb.append(System.getProperty("path.separator"));
            }
        }
        return sb.toString();
    }

    public static boolean compile(File src) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        String cp = buildClassPath(new File("lib/").getCanonicalPath() + "/*" );

        List<String> optionList = new ArrayList<String>();
        optionList.add("--release");
        optionList.add("10");

        optionList.add("-classpath");
        optionList.add(System.getProperty("java.class.path") + ";" + cp);

        Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(src));
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                optionList,
                null,
                compilationUnit);

        boolean r = task.call();

        if (!r) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                System.out.format("Error on line %d in %s%n",
                        diagnostic.getLineNumber(),
                        diagnostic.getSource().toUri());
            }
        }

        return r;
    }

    @BeforeClass
    public static void before() throws IOException, InterruptedException {
    }

    @ClassRule
    public static AvmRule avmRule = new AvmRule(true);
    private static Address sender = avmRule.getPreminedAccount();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void preimageTest() throws IOException, ParseException, InterruptedException, ClassNotFoundException {
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

        // OK, now that we have the contract, deploy it!
        List<String> classes = Arrays.asList("Verifier.java", "Fp.java", "Fp2.java", "G1.java", "G1Point.java", "G2.java", "G2Point.java", "Pairing.java", "Util.java");
        ArrayList<Class<?>> loaded = new ArrayList<>();
        URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(testFolder.getCanonicalPath() + "/avm-verifier/").toURI().toURL()});

        for (String c : classes) {
            boolean didCompile = compile(new File(testFolder.getCanonicalPath() + "/avm-verifier/" + c));
            if (didCompile) {
                loaded.add(classLoader.loadClass("org.acme.tetryon." + c.substring(0, c.lastIndexOf('.'))));
            }
        };

        byte[] dappBytes = avmRule.getDappBytes(loaded.get(0), null, 1, loaded.subList(1, loaded.size()).toArray(new Class<?>[loaded.size()-1]));
        AvmRule.ResultWrapper w = avmRule.deploy(sender, BigInteger.ZERO, dappBytes);
        Assert.assertTrue (w.getTransactionResult().energyUsed < 1_500_000);
        Address contract = w.getDappAddress();

        // OK, now test verify
        List<String> computeWitness = Arrays.asList(zokrates, "compute-witness", "-a", "337", "113569");
        call(computeWitness, testFolder);

        List<String> generateProof = Arrays.asList(zokrates, "generate-proof", "--proving-scheme", ps);
        call(generateProof, testFolder);

        final String generatedProof = FileUtils.readFileToString(new File(testFolder.getCanonicalPath() + "/proof.json"), (String) null);
        Proof proof = TestUtil.parseProof(generatedProof);
        BigInteger[] input = TestUtil.parseInput(generatedProof);

        byte[] txData = ABIUtil.encodeMethodArguments("verify", input, proof.serialize());
        AvmRule.ResultWrapper r = avmRule.call(sender, contract, BigInteger.ZERO, txData);

        Assert.assertTrue(r.getReceiptStatus().isSuccess());
        Assert.assertTrue(r.getTransactionResult().energyUsed < 500_000);
        Assert.assertTrue(new ABIDecoder(r.getTransactionResult().copyOfTransactionOutput().orElseThrow()).decodeOneBoolean());

        System.out.println("did it work?");
    }
}
