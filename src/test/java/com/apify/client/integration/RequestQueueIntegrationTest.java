package com.apify.client.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.apify.client.ApifyClient;
import com.apify.client.StorageListOptions;
import com.apify.client.requestqueue.BatchAddResult;
import com.apify.client.requestqueue.ListRequestsOptions;
import com.apify.client.requestqueue.RequestQueue;
import com.apify.client.requestqueue.RequestQueueClient;
import com.apify.client.requestqueue.RequestQueueOperationInfo;
import com.apify.client.requestqueue.RequestQueueRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestQueueIntegrationTest extends IntegrationBase {

  @Test
  void listRequestQueues() {
    ApifyClient client = requireClient();
    assertTrue(client.requestQueues().list(new StorageListOptions().limit(5L)).getTotal() >= 0);
  }

  @Test
  void getRequestQueue() {
    ApifyClient client = requireClient();
    RequestQueue rq = client.requestQueues().getOrCreate(uniqueName("rq-get"));
    try {
      var got = client.requestQueue(rq.getId()).get();
      assertTrue(got.isPresent());
      assertEquals(rq.getId(), got.get().getId());
    } finally {
      client.requestQueue(rq.getId()).delete();
    }
  }

  @Test
  void requestQueueCrudFlow() {
    ApifyClient client = requireClient();
    RequestQueue rq = client.requestQueues().getOrCreate(uniqueName("rq-crud"));
    try {
      RequestQueueClient queue = client.requestQueue(rq.getId());
      assertTrue(queue.get().isPresent());

      RequestQueueOperationInfo info =
          queue.addRequest(
              new RequestQueueRequest("https://example.com", "example").setMethod("GET"), false);
      assertTrue(info.getRequestId() != null && !info.getRequestId().isEmpty());

      var got = queue.getRequest(info.getRequestId());
      assertTrue(got.isPresent());
      assertEquals("https://example.com", got.get().getUrl());

      assertTrue(!queue.listHead(10L).getItems().isEmpty());
      queue.update(Map.of("name", uniqueName("rq-renamed")));
      queue.deleteRequest(info.getRequestId());
    } finally {
      client.requestQueue(rq.getId()).delete();
    }
  }

  @Test
  void requestQueuePaginateMultiplePages() {
    ApifyClient client = requireClient();
    RequestQueue rq = client.requestQueues().getOrCreate(uniqueName("rq-page"));
    try {
      RequestQueueClient queue = client.requestQueue(rq.getId());
      int total = 5;
      for (int i = 0; i < total; i++) {
        String url = "https://example.com/" + i;
        queue.addRequest(new RequestQueueRequest(url, url), false);
      }
      Set<String> seen = new HashSet<>();
      Iterator<RequestQueueRequest> it = queue.paginateRequests(2L);
      while (it.hasNext()) {
        seen.add(it.next().getUrl());
      }
      assertEquals(total, seen.size());
    } finally {
      client.requestQueue(rq.getId()).delete();
    }
  }

  @Test
  void requestQueueBatchAddRequests() {
    ApifyClient client = requireClient();
    RequestQueue rq = client.requestQueues().getOrCreate(uniqueName("rq-batch"));
    try {
      RequestQueueClient queue = client.requestQueue(rq.getId());
      int total = 30; // > 25, so the client must split into multiple chunks
      List<RequestQueueRequest> requests = new ArrayList<>();
      for (int i = 0; i < total; i++) {
        String url = "https://batch.example.com/" + i;
        requests.add(new RequestQueueRequest(url, url));
      }
      BatchAddResult result = queue.batchAddRequests(requests, false);
      assertEquals(total, result.getProcessedRequests().size());
    } finally {
      client.requestQueue(rq.getId()).delete();
    }
  }

  @Test
  void requestQueueLockLifecycle() {
    ApifyClient client = requireClient();
    RequestQueue rq = client.requestQueues().getOrCreate(uniqueName("rq-lock"));
    try {
      RequestQueueClient queue =
          client.requestQueue(rq.getId()).withClientKey("java-test-client-key");
      RequestQueueOperationInfo info =
          queue.addRequest(new RequestQueueRequest("https://lock.example.com", "lock"), false);
      queue.listRequests(new ListRequestsOptions());
      queue.listRequests(
          new ListRequestsOptions()
              .filter(
                  List.of(ListRequestsOptions.FILTER_LOCKED, ListRequestsOptions.FILTER_PENDING)));
      queue.listAndLockHead(60, 10L);
      queue.prolongRequestLock(info.getRequestId(), 30, false);
      queue.deleteRequestLock(info.getRequestId(), false);
      queue.unlockRequests();
    } finally {
      client.requestQueue(rq.getId()).delete();
    }
  }
}
