/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.tests.integration.jersey2846;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.external.ExternalTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;

import org.junit.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * JERSEY-2846 reproducer.
 *
 * @author Michal Gajdos
 */
public class Jersey2846ITCase extends JerseyTest {

    @Override
    protected Application configure() {
        return new Application();
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new ExternalTestContainerFactory();
    }

    @Override
    protected void configureClient(final ClientConfig config) {
        config.register(MultiPartFeature.class);
    }

    @Test
    public void tempFileDeletedAfterSuccessfulProcessing() throws Exception {
        _testSmall("SuccessfulMethod", 200);
    }

    @Test
    public void tempFileDeletedAfterExceptionInMethod() throws Exception {
        _testSmall("ExceptionInMethod", 500);
    }

    @Test
    public void tempFileDeletedAfterSuccessfulProcessingBigEntity() throws Exception {
        _testBig("SuccessfulMethod", 200);
    }

    @Test
    public void tempFileDeletedAfterExceptionInMethodBigEntity() throws Exception {
        _testBig("ExceptionInMethod", 500);
    }

    public void _testBig(final String path, final int status) throws Exception {
        final byte[] array = new byte[8196];
        Arrays.fill(array, (byte) 52);

        _test(path, status, array);
    }

    public void _testSmall(final String path, final int status) throws Exception {
        _test(path, status, "CONTENT");
    }

    public void _test(final String path, final int status, final Object entity) throws Exception {
        final String tempDir = System.getProperty("java.io.tmpdir");

        // Get number of matching MIME*tmp files (the number should be the same at the end of the test).
        final int expectedTempFiles = matchingTempFiles(tempDir);

        final FormDataMultiPart multipart = new FormDataMultiPart();
        final FormDataBodyPart bodypart = new FormDataBodyPart(FormDataContentDisposition.name("file").fileName("file").build(),
                entity, MediaType.TEXT_PLAIN_TYPE);
        multipart.bodyPart(bodypart);

        final Response response = target().path(path)
                .request()
                .post(Entity.entity(multipart, MediaType.MULTIPART_FORM_DATA));

        // Get Response ...
        assertThat(response.getStatus(), is(status));
        // Wait a second to make sure the files don't exist.
        Thread.sleep(1000);

        // Make sure that the message and it's parts have been closed and temporary files deleted.
        assertThat("Temporary files were not deleted", matchingTempFiles(tempDir), is(expectedTempFiles));
    }

    private int matchingTempFiles(final String tempDir) throws IOException {
        AtomicInteger count = new AtomicInteger(0);
        Files.walkFileTree(Paths.get(tempDir), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path name = file.getFileName();
                if ((name.startsWith("rep") || name.startsWith("MIME")) && name.endsWith("tmp")) {
                    count.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return count.get();
    }
}
