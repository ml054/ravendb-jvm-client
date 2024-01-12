package net.ravendb.client.counters;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.ravendb.client.RemoteTestBase;
import net.ravendb.client.documents.IDocumentStore;
import net.ravendb.client.documents.session.IDocumentQuery;
import net.ravendb.client.documents.session.IDocumentSession;
import net.ravendb.client.infrastructure.entities.Company;
import net.ravendb.client.infrastructure.entities.Employee;
import net.ravendb.client.infrastructure.entities.Order;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryOnCountersTest extends RemoteTestBase {

    @Test
    public void rawQuerySelectSingleCounter() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {

            try (IDocumentSession session = store.openSession()) {
                User user1 = new User();
                user1.setName("Jerry");

                User user2 = new User();
                user2.setName("Bob");

                User user3 = new User();
                user3.setName("Pigpen");

                session.store(user1, "users/1-A");
                session.store(user2, "users/2-A");
                session.store(user3, "users/3-A");

                session.countersFor("users/1-A").increment("Downloads", 100);
                session.countersFor("users/2-A").increment("Downloads", 200);
                session.countersFor("users/3-A").increment("Likes", 300);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                List<CounterResult> query = session
                        .advanced()
                        .rawQuery(CounterResult.class, "from users select counter(\"Downloads\") as downloads")
                        .toList();

                assertThat(query)
                        .hasSize(3);

                assertThat(query.get(0).getDownloads())
                        .isEqualTo(100);
                assertThat(query.get(1).getDownloads())
                        .isEqualTo(200);
                assertThat(query.get(2).getDownloads())
                        .isNull();
            }
        }
    }

    @Test
    public void rawQuerySelectSingleCounterWithDocAlias() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                User user1 = new User();
                user1.setName("Jerry");

                User user2 = new User();
                user2.setName("Bob");

                User user3 = new User();
                user3.setName("Pigpen");

                session.store(user1, "users/1-A");
                session.store(user2, "users/2-A");
                session.store(user3, "users/3-A");

                session.countersFor("users/1-A").increment("Downloads", 100);
                session.countersFor("users/2-A").increment("Downloads", 200);
                session.countersFor("users/3-A").increment("Likes", 300);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                List<CounterResult> query = session
                        .advanced()
                        .rawQuery(CounterResult.class, "from users as u select counter(u, \"Downloads\") as downloads")
                        .toList();

                assertThat(query)
                        .hasSize(3);

                assertThat(query.get(0).getDownloads())
                        .isEqualTo(100);
                assertThat(query.get(1).getDownloads())
                        .isEqualTo(200);
                assertThat(query.get(2).getDownloads())
                        .isNull();
            }
        }
    }

    @Test
    public void rawQuerySelectMultipleCounters() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                User user1 = new User();
                user1.setName("Jerry");

                User user2 = new User();
                user2.setName("Bob");

                User user3 = new User();
                user3.setName("Pigpen");

                session.store(user1, "users/1-A");
                session.store(user2, "users/2-A");
                session.store(user3, "users/3-A");

                session.countersFor("users/1-A").increment("downloads", 100);
                session.countersFor("users/1-A").increment("likes", 200);

                session.countersFor("users/2-A").increment("downloads", 400);
                session.countersFor("users/2-A").increment("likes", 800);

                session.countersFor("users/3-A").increment("likes", 1600);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                List<CounterResult> query = session.advanced().rawQuery(CounterResult.class, "from users select counter(\"downloads\"), counter(\"likes\")")
                        .toList();

                assertThat(query)
                        .hasSize(3);

                assertThat(query.get(0).downloads)
                        .isEqualTo(100);
                assertThat(query.get(0).likes)
                        .isEqualTo(200);

                assertThat(query.get(1).downloads)
                        .isEqualTo(400);
                assertThat(query.get(1).likes)
                        .isEqualTo(800);

                assertThat(query.get(2).downloads)
                        .isNull();
                assertThat(query.get(2).likes)
                        .isEqualTo(1600);
            }
        }
    }

    @Test
    public void rawQuerySimpleProjectionWithCounter() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                User user1 = new User();
                user1.setName("Jerry");

                User user2 = new User();
                user2.setName("Bob");

                User user3 = new User();
                user3.setName("Pigpen");

                session.store(user1, "users/1-A");
                session.store(user2, "users/2-A");
                session.store(user3, "users/3-A");

                session.countersFor("users/1-A").increment("downloads", 100);
                session.countersFor("users/2-A").increment("downloads", 200);
                session.countersFor("users/3-A").increment("likes", 400);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                List<CounterResult> query = session.advanced().rawQuery(CounterResult.class, "from users select name, counter('downloads')")
                        .toList();

                assertThat(query)
                        .hasSize(3);

                assertThat(query.get(0).getName())
                        .isEqualTo("Jerry");
                assertThat(query.get(0).getDownloads())
                        .isEqualTo(100);

                assertThat(query.get(1).getName())
                        .isEqualTo("Bob");
                assertThat(query.get(1).getDownloads())
                        .isEqualTo(200);

                assertThat(query.get(2).getName())
                        .isEqualTo("Pigpen");
                assertThat(query.get(2).getDownloads())
                        .isNull();
            }
        }
    }

    @Test
    public void rawQueryJsProjectionWithCounterRawValues() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                User user1 = new User();
                user1.setName("Jerry");

                User user2 = new User();
                user2.setName("Bob");

                User user3 = new User();
                user3.setName("Pigpen");

                session.store(user1, "users/1-A");
                session.store(user2, "users/2-A");
                session.store(user3, "users/3-A");

                session.countersFor("users/1-A").increment("downloads", 100);
                session.countersFor("users/1-A").increment("likes", 200);

                session.countersFor("users/2-A").increment("downloads", 300);
                session.countersFor("users/2-A").increment("likes", 400);

                session.countersFor("users/3-A").increment("likes", 500);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                List<CounterResult4> query = session
                        .advanced()
                        .rawQuery(CounterResult4.class, "from Users as u select { name: u.name, downloads: counter(u, 'downloads'), likes: counterRaw(u, 'likes') }")
                        .toList();

                assertThat(query)
                        .hasSize(3);

                assertThat(query.get(0).getName())
                        .isEqualTo("Jerry");
                assertThat(query.get(0).getDownloads())
                        .isEqualTo(100);
                assertThat(query.get(0).getLikes().values().iterator().next())
                        .isEqualTo(200);

                assertThat(query.get(1).getName())
                        .isEqualTo("Bob");
                assertThat(query.get(1).getDownloads())
                        .isEqualTo(300);
                assertThat(query.get(1).getLikes().values().iterator().next())
                        .isEqualTo(400);

                assertThat(query.get(2).getName())
                        .isEqualTo("Pigpen");
                assertThat(query.get(2).getLikes().values().iterator().next())
                        .isEqualTo(500);
                assertThat(query.get(2).getDownloads())
                        .isNull();
            }
        }
    }

    @Test
    public void sessionQueryIncludeSingleCounter() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setCompany("companies/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setCompany("companies/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setCompany("companies/3-A");
                session.store(order3, "orders/3-A");

                session.countersFor("orders/1-A").increment("downloads", 100);
                session.countersFor("orders/2-A").increment("downloads", 200);
                session.countersFor("orders/3-A").increment("downloads", 300);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i.includeCounter("downloads"));

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include counters('downloads')");

                List<Order> queryResult = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                Order order = queryResult.get(0);
                assertThat(order.getId())
                        .isEqualTo("orders/1-A");

                Long val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(100);

                order = queryResult.get(1);
                assertThat(order.getId())
                        .isEqualTo("orders/2-A");
                val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(200);

                order = queryResult.get(2);
                assertThat(order.getId())
                        .isEqualTo("orders/3-A");
                val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(300);

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void sessionQueryIncludeMultipleCounters() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setCompany("companies/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setCompany("companies/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setCompany("companies/3-A");
                session.store(order3, "orders/3-A");

                session.countersFor("orders/1-A").increment("downloads", 100);
                session.countersFor("orders/2-A").increment("downloads", 200);
                session.countersFor("orders/3-A").increment("downloads", 300);

                session.countersFor("orders/1-A").increment("likes", 1000);
                session.countersFor("orders/2-A").increment("likes", 2000);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i.includeCounters(new String[]{"downloads", "likes"}));

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include counters('downloads'),counters('likes')");

                List<Order> queryResult = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included counters should be in cache
                Order order = queryResult.get(0);
                assertThat(order.getId())
                        .isEqualTo("orders/1-A");

                Long val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(100);
                val = session.countersFor(order).get("likes");
                assertThat(val)
                        .isEqualTo(1000);

                order = queryResult.get(1);
                assertThat(order.getId())
                        .isEqualTo("orders/2-A");
                val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(200);
                val = session.countersFor(order).get("likes");
                assertThat(val)
                        .isEqualTo(2000);

                order = queryResult.get(2);
                assertThat(order.getId())
                        .isEqualTo("orders/3-A");
                val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(300);

                //should register missing counters
                val = session.countersFor(order).get("likes");
                assertThat(val)
                        .isNull();

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void sessionQueryIncludeAllCounters() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setCompany("companies/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setCompany("companies/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setCompany("companies/3-A");
                session.store(order3, "orders/3-A");

                session.countersFor("orders/1-A").increment("Downloads", 100);
                session.countersFor("orders/2-A").increment("Downloads", 200);
                session.countersFor("orders/3-A").increment("Downloads", 300);

                session.countersFor("orders/1-A").increment("Likes", 1000);
                session.countersFor("orders/2-A").increment("Likes", 2000);

                session.countersFor("orders/1-A").increment("Votes", 10000);
                session.countersFor("orders/3-A").increment("Cats", 5);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i.includeAllCounters());

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include counters()");

                List<Order> queryResult = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included counters should be in cache
                Order order = queryResult.get(0);
                assertThat(order.getId())
                        .isEqualTo("orders/1-A");

                Map<String, Long> dic = session.countersFor(order).getAll();
                assertThat(dic)
                        .hasSize(3)
                        .containsEntry("Downloads", 100L)
                        .containsEntry("Likes", 1000L)
                        .containsEntry("Votes", 10000L);

                order = queryResult.get(1);
                assertThat(order.getId())
                        .isEqualTo("orders/2-A");
                dic = session.countersFor(order).getAll();

                assertThat(dic)
                        .hasSize(2)
                        .containsEntry("Downloads", 200L)
                        .containsEntry("Likes", 2000L);

                order = queryResult.get(2);
                assertThat(order.getId())
                        .isEqualTo("orders/3-A");
                dic = session.countersFor(order).getAll();
                assertThat(dic)
                        .hasSize(2)
                        .containsEntry("Downloads", 300L)
                        .containsEntry("Cats", 5L);

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void sessionQueryIncludeCounterAndDocument() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setCompany("companies/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setCompany("companies/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setCompany("companies/3-A");
                session.store(order3, "orders/3-A");

                Company company1 = new Company();
                company1.setName("HR");
                session.store(company1, "companies/1-A");

                Company company2 = new Company();
                company2.setName("HP");
                session.store(company2, "companies/2-A");

                Company company3 = new Company();
                company3.setName("Google");
                session.store(company3, "companies/3-A");

                session.countersFor("orders/1-A").increment("downloads", 100);
                session.countersFor("orders/2-A").increment("downloads", 200);
                session.countersFor("orders/3-A").increment("downloads", 300);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i
                                .includeCounter("downloads")
                                .includeDocuments("company"));

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include company,counters('downloads')");

                List<Order> queryResult = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included documents should be in cache
                session.load(User.class, "companies/1-A", "companies/2-A", "companies/3-A");
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included counters should be in cache
                Order order = queryResult.get(0);
                assertThat(order.getId())
                        .isEqualTo("orders/1-A");
                Long val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(100);

                order = queryResult.get(1);
                assertThat(order.getId())
                        .isEqualTo("orders/2-A");
                val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(200);

                order = queryResult.get(2);
                assertThat(order.getId())
                        .isEqualTo("orders/3-A");
                val = session.countersFor(order).get("downloads");
                assertThat(val)
                        .isEqualTo(300);

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void sessionQueryIncludeCounterOfRelatedDocument() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setEmployee("employees/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setEmployee("employees/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setEmployee("employees/3-A");
                session.store(order3, "orders/3-A");

                Employee employee1 = new Employee();
                employee1.setFirstName("Aviv");
                session.store(employee1, "employees/1-A");

                Employee employee2 = new Employee();
                employee2.setFirstName("Jerry");
                session.store(employee2, "employees/2-A");

                Employee employee3 = new Employee();
                employee3.setFirstName("Bob");
                session.store(employee3, "employees/3-A");

                session.countersFor("employees/1-A").increment("downloads", 100);
                session.countersFor("employees/2-A").increment("downloads", 200);
                session.countersFor("employees/3-A").increment("downloads", 300);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i.includeCounter("employee", "downloads"));

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include counters(employee, 'downloads')");

                List<Order> results = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included counters should be in cache
                Long val = session.countersFor("employees/1-A").get("downloads");
                assertThat(val)
                        .isEqualTo(100);

                val = session.countersFor("employees/2-A").get("downloads");
                assertThat(val)
                        .isEqualTo(200);

                val = session.countersFor("employees/3-A").get("downloads");
                assertThat(val)
                        .isEqualTo(300);

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void sessionQueryIncludeCountersOfRelatedDocument() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setEmployee("employees/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setEmployee("employees/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setEmployee("employees/3-A");
                session.store(order3, "orders/3-A");

                Employee employee1 = new Employee();
                employee1.setFirstName("Aviv");
                session.store(employee1, "employees/1-A");

                Employee employee2 = new Employee();
                employee2.setFirstName("Jerry");
                session.store(employee2, "employees/2-A");

                Employee employee3 = new Employee();
                employee3.setFirstName("Bob");
                session.store(employee3, "employees/3-A");

                session.countersFor("employees/1-A").increment("downloads", 100);
                session.countersFor("employees/2-A").increment("downloads", 200);
                session.countersFor("employees/3-A").increment("downloads", 300);

                session.countersFor("employees/1-A").increment("likes", 1000);
                session.countersFor("employees/2-A").increment("likes", 2000);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i.includeCounters("employee", new String[]{"downloads", "likes"}));

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include counters(employee, 'downloads'),counters(employee, 'likes')");

                List<Order> results = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included counters should be in cache
                Map<String, Long> dic = session.countersFor("employees/1-A").get(Arrays.asList("downloads", "likes"));
                assertThat(dic)
                        .containsEntry("downloads", 100L)
                        .containsEntry("likes", 1000L);

                dic = session.countersFor("employees/2-A").get(Arrays.asList("downloads", "likes"));
                assertThat(dic)
                        .containsEntry("downloads", 200L)
                        .containsEntry("likes", 2000L);

                dic = session.countersFor("employees/3-A").get(Arrays.asList("downloads", "likes"));
                assertThat(dic)
                        .containsEntry("downloads", 300L);

                assertThat(dic.get("likes"))
                        .isNull();

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void sessionQueryIncludeCountersOfDocumentAndOfRelatedDocument() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setEmployee("employees/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setEmployee("employees/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setEmployee("employees/3-A");
                session.store(order3, "orders/3-A");

                Employee employee1 = new Employee();
                employee1.setFirstName("Aviv");
                session.store(employee1, "employees/1-A");

                Employee employee2 = new Employee();
                employee2.setFirstName("Jerry");
                session.store(employee2, "employees/2-A");

                Employee employee3 = new Employee();
                employee3.setFirstName("Bob");
                session.store(employee3, "employees/3-A");

                session.countersFor("orders/1-A").increment("likes", 100);
                session.countersFor("orders/2-A").increment("likes", 200);
                session.countersFor("orders/3-A").increment("likes", 300);

                session.countersFor("employees/1-A").increment("downloads", 1000);
                session.countersFor("employees/2-A").increment("downloads", 2000);
                session.countersFor("employees/3-A").increment("downloads", 3000);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i.includeCounter("likes").includeCounter("employee", "downloads"));

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include counters('likes'),counters(employee, 'downloads')");

                List<Order> orders = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included counters should be in cache
                Order order = orders.get(0);
                assertThat(order.getId())
                        .isEqualTo("orders/1-A");
                Long val = session.countersFor(order).get("likes");
                assertThat(val)
                        .isEqualTo(100);

                order = orders.get(1);
                assertThat(order.getId())
                        .isEqualTo("orders/2-A");
                val = session.countersFor(order).get("likes");
                assertThat(val)
                        .isEqualTo(200);

                order = orders.get(2);
                assertThat(order.getId())
                        .isEqualTo("orders/3-A");
                val = session.countersFor(order).get("likes");
                assertThat(val)
                        .isEqualTo(300);

                val = session.countersFor("employees/1-A").get("downloads");
                assertThat(val)
                        .isEqualTo(1000);

                val = session.countersFor("employees/2-A").get("downloads");
                assertThat(val)
                        .isEqualTo(2000);

                val = session.countersFor("employees/3-A").get("downloads");
                assertThat(val)
                        .isEqualTo(3000);

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void sessionQueryIncludeAllCountersOfDocumentAndOfRelatedDocument() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order1 = new Order();
                order1.setEmployee("employees/1-A");
                session.store(order1, "orders/1-A");

                Order order2 = new Order();
                order2.setEmployee("employees/2-A");
                session.store(order2, "orders/2-A");

                Order order3 = new Order();
                order3.setEmployee("employees/3-A");
                session.store(order3, "orders/3-A");

                Employee employee1 = new Employee();
                employee1.setFirstName("Aviv");
                session.store(employee1, "employees/1-A");

                Employee employee2 = new Employee();
                employee2.setFirstName("Jerry");
                session.store(employee2, "employees/2-A");

                Employee employee3 = new Employee();
                employee3.setFirstName("Bob");
                session.store(employee3, "employees/3-A");

                session.countersFor("orders/1-A").increment("likes", 100);
                session.countersFor("orders/2-A").increment("likes", 200);
                session.countersFor("orders/3-A").increment("likes", 300);

                session.countersFor("orders/1-A").increment("downloads", 1000);
                session.countersFor("orders/2-A").increment("downloads", 2000);

                session.countersFor("employees/1-A").increment("likes", 100);
                session.countersFor("employees/2-A").increment("likes", 200);
                session.countersFor("employees/3-A").increment("likes", 300);
                session.countersFor("employees/1-A").increment("downloads", 1000);
                session.countersFor("employees/2-A").increment("downloads", 2000);
                session.countersFor("employees/3-A").increment("cats", 5);

                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                IDocumentQuery<Order> query = session.query(Order.class)
                        .include(i -> i
                                .includeAllCounters()
                                .includeAllCounters("employee"));

                assertThat(query.toString())
                        .isEqualTo("from 'Orders' include counters(),counters(employee)");

                List<Order> orders = query.toList();
                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);

                // included counters should be in cache
                Order order = orders.get(0);
                assertThat(order.getId())
                        .isEqualTo("orders/1-A");
                Map<String, Long> dic = session.countersFor(order).getAll();
                assertThat(dic)
                        .hasSize(2)
                        .containsEntry("likes", 100L)
                        .containsEntry("downloads", 1000L);

                order = orders.get(1);
                assertThat(order.getId())
                        .isEqualTo("orders/2-A");

                dic = session.countersFor(order).getAll();
                assertThat(dic)
                        .hasSize(2)
                        .containsEntry("likes", 200L)
                        .containsEntry("downloads", 2000L);

                order = orders.get(2);
                assertThat(order.getId())
                        .isEqualTo("orders/3-A");

                dic = session.countersFor(order).getAll();
                assertThat(dic)
                        .hasSize(1)
                        .containsEntry("likes", 300L);

                dic = session.countersFor("employees/1-A").getAll();
                assertThat(dic)
                        .hasSize(2)
                        .containsEntry("likes", 100L)
                        .containsEntry("downloads", 1000L);

                dic = session.countersFor("employees/2-A").getAll();
                assertThat(dic)
                        .hasSize(2)
                        .containsEntry("likes", 200L)
                        .containsEntry("downloads", 2000L);

                dic = session.countersFor("employees/3-A").getAll();
                assertThat(dic)
                        .hasSize(2)
                        .containsEntry("likes", 300L)
                        .containsEntry("cats", 5L);

                assertThat(session.advanced().getNumberOfRequests())
                        .isEqualTo(1);
            }
        }
    }

    @Test
    public void countersShouldBeCachedOnCollection() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order = new Order();
                order.setCompany("companies/1-A");
                session.store(order, "orders/1-A");
                session.countersFor("orders/1-A").increment("downloads", 100);
                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                session.query(Order.class)
                        .include(i -> i.includeCounter("downloads"))
                        .toList();

                Long counterValue = session.countersFor("orders/1-A")
                        .get("downloads");
                assertThat(counterValue)
                        .isEqualTo(100);

                session.countersFor("orders/1-A").increment("downloads", 200);
                session.saveChanges();

                session.query(Order.class)
                        .include(i -> i.includeCounters(new String[] { "downloads" }))
                        .toList();

                counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(300);

                session.countersFor("orders/1-A").increment("downloads", 200);
                session.saveChanges();

                session.query(Order.class)
                        .include(i -> i.includeCounter("downloads"))
                        .toList();

                counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(500);
            }

            try (IDocumentSession session = store.openSession()) {
                session.load(Order.class, "orders/1-A", i -> i.includeCounter("downloads"));
                Long counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(500);

                session.countersFor("orders/1-A").increment("downloads", 200);
                session.saveChanges();

                session.load(Order.class, "order/1-A", i -> i.includeCounter("downloads"));
                counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(700);
            }
        }
    }

    @Test
    public void countersShouldBeCachedOnAllDocsCollection() throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order = new Order();
                order.setCompany("companies/1-A");
                session.store(order, "orders/1-A");
                session.countersFor("orders/1-A").increment("downloads", 100);
                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                session.advanced()
                        .rawQuery(ObjectNode.class, "from @all_docs include counters($p0)")
                        .addParameter("p0", new String[] { "downloads" })
                        .toList();

                Long counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(100);

                session.countersFor("orders/1-A").increment("downloads", 200);
                session.saveChanges();

                session.advanced().rawQuery(ObjectNode.class, "from @all_docs include counters()")
                        .toList();
                counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(300);

                session.countersFor("orders/1-A").increment("downloads", 200);
                session.saveChanges();

                session
                        .advanced()
                        .rawQuery(ObjectNode.class, "from @all_docs include counters($p0)")
                        .addParameter("p0", new String[] { "downloads" })
                        .toList();

                counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(500);
            }
        }
    }

    @Test
    public void countersCachingShouldHandleDeletion_includeCounterDownload() throws Exception {
        countersCachingShouldHandleDeletion(session -> session.query(Order.class).include(i -> i.includeCounter("downloads")).toList(), null);
    }

    @Test
    public void countersCachingShouldHandleDeletion_includeCounterUpload() throws Exception {
        countersCachingShouldHandleDeletion(session -> session.query(Order.class).include(i -> i.includeCounter("uploads")).toList(), 300L);
    }

    @Test
    public void countersCachingShouldHandleDeletion_includeCounters() throws Exception {
        countersCachingShouldHandleDeletion(session -> session.query(Order.class).include(i -> i.includeCounters(new String[] { "downloads", "uploads", "bugs" })).toList(), null);
    }

    @Test
    public void countersCachingShouldHandleDeletion_includeAllCounters() throws Exception {
        countersCachingShouldHandleDeletion(session -> session.query(Order.class).include(i -> i.includeAllCounters()).toList(), null);
    }

    private void countersCachingShouldHandleDeletion(Consumer<IDocumentSession> sessionConsumer, Long expectedCounterValue) throws Exception {
        try (IDocumentStore store = getDocumentStore()) {
            try (IDocumentSession session = store.openSession()) {
                Order order = new Order();
                order.setCompany("companies/1-A");
                session.store(order, "orders/1-A");

                session.countersFor("orders/1-A").increment("downloads", 100);
                session.countersFor("orders/1-A").increment("uploads", 123);
                session.countersFor("orders/1-A").increment("bugs", 0xDEAD);
                session.saveChanges();
            }

            try (IDocumentSession session = store.openSession()) {
                session.query(Order.class)
                        .include(i -> i.includeCounter("downloads"))
                        .toList();

                Long counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(100);

                try (IDocumentSession writeSession = store.openSession()) {
                    writeSession.countersFor("orders/1-A").increment("downloads", 200);
                    writeSession.saveChanges();
                }

                session.query(Order.class).include(i -> i.includeCounter("downloads")).toList();

                counterValue = session.countersFor("orders/1-A").get("downloads");
                assertThat(counterValue)
                        .isEqualTo(300);
                session.saveChanges();

                try (IDocumentSession writeSession = store.openSession()) {
                    writeSession.countersFor("orders/1-A").delete("downloads");
                    writeSession.saveChanges();
                }

                sessionConsumer.accept(session);

                counterValue = session.countersFor("orders/1-A").get("downloads");

                assertThat(counterValue)
                        .isEqualTo(expectedCounterValue);
            }
        }
    }

    public static class User {
        private String name;
        private int age;
        private String friendId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getFriendId() {
            return friendId;
        }

        public void setFriendId(String friendId) {
            this.friendId = friendId;
        }
    }

    public static class CounterResult {
        private Long downloads;
        private Long likes;
        private String name;

        public Long getDownloads() {
            return downloads;
        }

        public void setDownloads(Long downloads) {
            this.downloads = downloads;
        }

        public Long getLikes() {
            return likes;
        }

        public void setLikes(Long likes) {
            this.likes = likes;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class CounterResult2 {
        private Long downloadsCount;
        private Long likesCount;

        public Long getDownloadsCount() {
            return downloadsCount;
        }

        public void setDownloadsCount(Long downloadsCount) {
            this.downloadsCount = downloadsCount;
        }

        public Long getLikesCount() {
            return likesCount;
        }

        public void setLikesCount(Long likesCount) {
            this.likesCount = likesCount;
        }
    }

    public static class CounterResult3 {
        private Map<String, Long> downloads;

        public Map<String, Long> getDownloads() {
            return downloads;
        }

        public void setDownloads(Map<String, Long> downloads) {
            this.downloads = downloads;
        }
    }

    public static class CounterResult4 {
        private Long downloads;
        private String name;
        private Map<String, Long> likes;

        public Long getDownloads() {
            return downloads;
        }

        public void setDownloads(Long downloads) {
            this.downloads = downloads;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Long> getLikes() {
            return likes;
        }

        public void setLikes(Map<String, Long> likes) {
            this.likes = likes;
        }
    }

    public static class CounterResult5 {
        private Map<String, Long> counters;

        public Map<String, Long> getCounters() {
            return counters;
        }

        public void setCounters(Map<String, Long> counters) {
            this.counters = counters;
        }
    }

    public static class CounterResult6 {
        private Long counter;

        public Long getCounter() {
            return counter;
        }

        public void setCounter(Long counter) {
            this.counter = counter;
        }
    }

    public static class CounterResult7 {
        private Long downloads;
        private Long friendsDownloads;
        private String name;

        public Long getDownloads() {
            return downloads;
        }

        public void setDownloads(Long downloads) {
            this.downloads = downloads;
        }

        public Long getFriendsDownloads() {
            return friendsDownloads;
        }

        public void setFriendsDownloads(Long friendsDownloads) {
            this.friendsDownloads = friendsDownloads;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
