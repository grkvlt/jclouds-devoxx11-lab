/*
 * @(#)BlobWriterReader.java     2 Nov 2011
 *
 * Copyright © 2010 Andrew Phillips.
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package com.devoxx.y2011.labs.jclouds.exercise3;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.jclouds.blobstore.AsyncBlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.BlobStoreContextFactory;
import org.jclouds.logging.log4j.config.Log4JLoggingModule;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

/**
 * @author aphillips
 * @since 2 Nov 2011
 *
 */
public class FileUploaderC {
    private static final int QUERY_RETRY_INTERVAL_MILLIS = 100;
    
    private final BlobStoreContext ctx;
    
    public FileUploaderC(String provider, String identity, String credential) {
        ctx = new BlobStoreContextFactory().createContext(provider, identity, credential, 
                ImmutableSet.of(new Log4JLoggingModule()));
    }
    
    public void uploadFile(File file) throws IOException, InterruptedException, ExecutionException {
        AsyncBlobStore store = ctx.getAsyncBlobStore();
        final String containerName = "test-container-x";
        long fileSize = file.length();
        System.out.format("Starting upload of %d bytes%n", fileSize);
        String filename = file.getName();
        store.putBlob(containerName, store.blobBuilder(filename).payload(file).build());
        waitUntilExists(store, containerName, filename);
        waitUntilAvailable(store, containerName, filename);
        byte[] payloadRead = ByteStreams.toByteArray(
                store.getBlob(containerName, filename).get().getPayload().getInput());
        System.out.format("Retrieved blob size: %d bytes%n", payloadRead.length);
        System.out.format("Blob metadata: %s%n", store.blobMetadata(containerName, filename).get().getContentMetadata());
        tryDeleteContainer(store, containerName);
    }
    
    private static void waitUntilExists(AsyncBlobStore store, String containerName, String blobName) throws InterruptedException, ExecutionException {
        while (!store.blobExists(containerName, blobName).get()) {
            TimeUnit.MILLISECONDS.sleep(QUERY_RETRY_INTERVAL_MILLIS);
            System.out.println("Waiting for blob to 'exist'");
        }
        System.out.println("Blob exists");
    }
    
    private static void waitUntilAvailable(AsyncBlobStore store, String containerName, String blobName) throws InterruptedException, ExecutionException {
        while (store.blobMetadata(containerName, blobName).get()
                .getContentMetadata().getContentLength() == null) {
            TimeUnit.MILLISECONDS.sleep(QUERY_RETRY_INTERVAL_MILLIS);
            System.out.println("Waiting for blob to become available");
        }
        System.out.println("Blob available");
    }
    
    private static void tryDeleteContainer(AsyncBlobStore store, String containerName) {
        try {
            // block until complete
            store.deleteContainer(containerName).get();
        } catch (Exception exception) {
            System.err.format("Unable to delete container due to: %s%n", exception.getMessage());
        }
    }
    
    public void cleanup() {
        ctx.close();
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.format("%nUsage: %s <provider> <identity> <credential>%n", FileUploaderC.class.getSimpleName());
            System.exit(1);
        }
        FileUploaderC uploader = new FileUploaderC(args[0], args[1], args[2]);
        try {
            uploader.uploadFile(new File("src/main/resources/s3-qrc.pdf"));
        } finally {
            uploader.cleanup();
        }
    }
}
