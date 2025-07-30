/*
 * Copyright (C) 2010-2021 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.bytecode;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Lombok;
import lombok.Lookup;

import org.junit.Ignore;
import org.junit.Test;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Lookup(field = "value", constructorArgumentOrdinal = 0, defaultValue = "TEST2")
enum LookupTest{
	TEST1(11),
	TEST2(22);

	@Getter
	private final int value;
}

public class TestLookupAnnotation {
	
	private static ClassFileMetaData lookup = create(new File("test/bytecode/resource/LookupEnum.java"));
	
	@Test
	public void testGetClassName() {
		assertTrue(lookup.containsUtf8("LookupEnum"));
		assertEquals("LookupEnum", lookup.getClassName());
	}
	
	@Ignore
	public void testLookup() {
		//ToDo: Find a way to utilize AnnotationProcessor in tests
		assertEquals(LookupTest.TEST1, LookupTest.lookup(11));
		assertEquals(null, LookupTest.lookup(77));
	}
	
	private static ClassFileMetaData create(File file) {
		return new ClassFileMetaData(compile(file));
	}
	
	static byte[] compile(File file) {
		try {
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			if (compiler == null) {
				// The 'auto-find my compiler' code in java6 works because it hard-codes `c.s.t.j.a.JavacTool`, which we put on the classpath.
				// On java8, it fails, because it looks for tools.jar, which won't be there.
				// on J11+ place it succeeds again, as we run those on the real JDKs.
				
				// Thus, let's try this, in cae we're on java 8:
				
				try {
					compiler = (JavaCompiler) Class.forName("com.sun.tools.javac.api.JavacTool").getConstructor().newInstance();
				} catch (Exception e) {
					compiler = null;
				}
			}
			
			if (compiler == null) throw new RuntimeException("No javac tool is available in this distribution. Using an old JRE perhaps?");
			
			File tempDir = getTempDir();
			tempDir.mkdirs();
			List<String> options = Arrays.asList(/* "-proc:none", */ "-d", tempDir.getAbsolutePath());
			
			StringWriter captureWarnings = new StringWriter();
			final StringBuilder compilerErrors = new StringBuilder();
			DiagnosticListener<JavaFileObject> diagnostics = new DiagnosticListener<JavaFileObject>() {
				@Override public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
					compilerErrors.append(diagnostic.toString()).append("\n");
				}
			};
			
			CompilationTask task = compiler.getTask(captureWarnings, null, diagnostics, options, null, Collections.singleton(new ContentBasedJavaFileObject(file.getPath(), readFileAsString(file))));
			task.setProcessors(List.of(new lombok.core.AnnotationProcessor()));
			Boolean taskResult = task.call();
			assertTrue("Compilation task didn't succeed: \n<Warnings and Errors>\n" + compilerErrors.toString() + "\n" + captureWarnings.toString() + "\n</Warnings and Errors>", taskResult);
			return PostCompilerApp.readFile(new File(tempDir, file.getName().replaceAll("\\.java$", ".class")));
		} catch (Exception e) {
			throw Lombok.sneakyThrow(e);
		}
	}
	
	private static File getTempDir() {
		String[] rawDirs = {
				System.getProperty("java.io.tmpdir"),
				"/tmp",
				"C:\\Windows\\Temp"
		};
		
		for (String dir : rawDirs) {
			if (dir == null) continue;
			File f = new File(dir);
			if (!f.isDirectory()) continue;
			return new File(f, "lombok.bytecode-test");
		}
		
		return new File("./build/tmp");
	}
	
	static class ContentBasedJavaFileObject extends SimpleJavaFileObject {
		private final String content;
		
		protected ContentBasedJavaFileObject(String name, String content) {
			super(new File(name).toURI(), Kind.SOURCE);
			this.content = content;
		}
		
		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
			return content;
		}
	}
	
	private static String readFileAsString(File file) {
		try {
			FileInputStream in = new FileInputStream(file);
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
				StringWriter writer = new StringWriter();
				String line = reader.readLine();
				while(line != null) {
					writer.append(line).append("\n");
					line = reader.readLine();
				}
				reader.close();
				writer.close();
				return writer.toString();
			} finally {
				in.close();
			}
		} catch (Exception e) {
			throw Lombok.sneakyThrow(e);
		}
	}
}
