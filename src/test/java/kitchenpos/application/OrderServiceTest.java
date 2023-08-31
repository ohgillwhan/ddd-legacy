package kitchenpos.application;

import kitchenpos.domain.*;
import kitchenpos.fixture.MenuProductFixture;
import kitchenpos.fixture.OrderFixture;
import kitchenpos.infra.KitchenridersClient;
import kitchenpos.support.BaseServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static kitchenpos.fixture.MenuFixture.createMenu;
import static kitchenpos.fixture.MenuGroupFixture.createMenuGroup;
import static kitchenpos.fixture.MenuProductFixture.createMenuProductWithDefaultId;
import static kitchenpos.fixture.OrderFixture.createDeliveryOrder;
import static kitchenpos.fixture.OrderFixture.createOrder;
import static kitchenpos.fixture.OrderLineItemFixture.createOrderLineItem;
import static kitchenpos.fixture.OrderTableFixture.createOrderTable;
import static kitchenpos.fixture.ProductFixture.createProduct;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doNothing;

class OrderServiceTest extends BaseServiceTest {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final MenuGroupRepository menuGroupRepository;
    private final ProductRepository productRepository;
    private final MenuRepository menuRepository;
    private final OrderTableRepository orderTableRepository;

    @MockBean
    private KitchenridersClient kitchenridersClient;

    public OrderServiceTest(final OrderService orderService, final OrderRepository orderRepository, final MenuGroupRepository menuGroupRepository, final ProductRepository productRepository, final MenuRepository menuRepository, final OrderTableRepository orderTableRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.menuGroupRepository = menuGroupRepository;
        this.productRepository = productRepository;
        this.menuRepository = menuRepository;
        this.orderTableRepository = orderTableRepository;
    }

    @DisplayName("주문 요청")
    @Nested
    class create {
        @DisplayName("주문 요청이 가능하다.")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu));
            final Order order = createDeliveryOrder(orderLineItems);

            final Order createdOrder = orderService.create(order);

            final Order foundOrder = orderRepository.findAll().get(0);

            assertAll(
                    () -> assertThat(createdOrder.getId()).isNotNull(),
                    () -> assertThat(createdOrder.getType()).isEqualTo(order.getType()),
                    () -> assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.WAITING),
                    () -> assertThat(createdOrder.getOrderDateTime()).isBeforeOrEqualTo(LocalDateTime.now()),
                    () -> assertThat(createdOrder.getOrderLineItems())
                            .map(OrderLineItemFields::new)
                            .containsExactlyElementsOf(order.getOrderLineItems().stream().map(OrderLineItemFields::new).collect(Collectors.toList())),
                    () -> assertThat(createdOrder.getDeliveryAddress()).isEqualTo(order.getDeliveryAddress()),
                    () -> assertThat(createdOrder.getOrderTable()).isEqualTo(order.getOrderTable()),
                    () -> assertThat(createdOrder.getOrderTableId()).isEqualTo(order.getOrderTableId()),
                    () -> assertThat(foundOrder.getId()).isEqualTo(createdOrder.getId())
            );
        }

        @DisplayName("주문의 수령 방법은 필수이다.")
        @Test
        void test2() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu));
            final Order order = createOrder(null, null, orderLineItems, null);

            assertThatIllegalArgumentException().isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("주문의 주문 목록은 비어있으면 안된다")
        @Test
        void test3() {
            final Order order = createOrder(OrderType.DELIVERY, "delivery", null, null);

            assertThatIllegalArgumentException().isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("주문의 주문 목록은 필수이다")
        @Test
        void test4() {
            final Order order = createOrder(OrderType.DELIVERY, "delivery", Collections.emptyList(), null);

            assertThatIllegalArgumentException().isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("주문 수령 방법이 배달일 경우 배달지 주소가 있으면 주문이 가능하다")
        @Test
        void test5() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu));
            final Order order = createOrder(OrderType.DELIVERY, "청주시", orderLineItems, null);

            final Order createdOrder = orderService.create(order);

            assertAll(
                    () -> assertThat(createdOrder.getId()).isNotNull(),
                    () -> assertThat(createdOrder.getType()).isEqualTo(order.getType()),
                    () -> assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.WAITING),
                    () -> assertThat(createdOrder.getOrderDateTime()).isBeforeOrEqualTo(LocalDateTime.now()),
                    () -> assertThat(createdOrder.getOrderLineItems())
                            .map(OrderLineItemFields::new)
                            .containsExactlyElementsOf(order.getOrderLineItems().stream().map(OrderLineItemFields::new).collect(Collectors.toList())),
                    () -> assertThat(createdOrder.getDeliveryAddress()).isEqualTo(order.getDeliveryAddress())
            );
        }

        @DisplayName("주문 수령 방법이 배달일 경우 배달지 주소는 필수이다.")
        @NullAndEmptySource
        @ParameterizedTest
        void test6(final String deliveryAddress) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu));
            final Order order = createOrder(OrderType.DELIVERY, deliveryAddress, orderLineItems, null);

            assertThatIllegalArgumentException().isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("주문 수령 방법이 매장내 식사의 경우 테이블 착석중이면 주문이 가능하다.")
        @ParameterizedTest
        @ValueSource(longs = {-1, 0, 1})
        void test7(final long quantity) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, quantity));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, true));
            final Order order = createOrder(OrderType.EAT_IN, null, orderLineItems, orderTable);

            final Order createdOrder = orderService.create(order);

            assertAll(
                    () -> assertThat(createdOrder.getId()).isNotNull(),
                    () -> assertThat(createdOrder.getType()).isEqualTo(order.getType()),
                    () -> assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.WAITING),
                    () -> assertThat(createdOrder.getOrderDateTime()).isBeforeOrEqualTo(LocalDateTime.now()),
                    () -> assertThat(createdOrder.getOrderLineItems())
                            .map(OrderLineItemFields::new)
                            .containsExactlyElementsOf(order.getOrderLineItems().stream().map(OrderLineItemFields::new).collect(Collectors.toList())),
                    () -> assertThat(createdOrder.getDeliveryAddress()).isEqualTo(order.getDeliveryAddress()),
                    () -> assertThat(createdOrder.getOrderTable()).isEqualTo(order.getOrderTable())
            );
        }

        @DisplayName("주문 수령 방법이 매장내 식사의 경우 테이블은 필수이다")
        @Test
        void test8() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu));
            final OrderTable orderTable = createOrderTable(UUID.randomUUID(), 5, true);
            final Order order = createOrder(OrderType.EAT_IN, null, orderLineItems, orderTable);

            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("주문 수령 방법이 매장내 식사의 경우 테이블이 미착석이면 주문을 실패한다.")
        @Test
        void test9() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = createOrder(OrderType.EAT_IN, null, orderLineItems, orderTable);

            assertThatIllegalStateException().isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("매장 식사의 경우가 아닐 경우 주문 목록의 메뉴 수랑은 필수 이다.")
        @ParameterizedTest
        @EnumSource(value = OrderType.class, names = {"EAT_IN"}, mode = EnumSource.Mode.EXCLUDE)
        void test10(final OrderType orderType) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, -1));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = createOrder(orderType, "주소", orderLineItems, orderTable);

            assertThatIllegalArgumentException().isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("표출된 메뉴만 주문 요청이 가능하다.")
        @Test
        void test11() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, false, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, -1));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = createOrder(OrderType.EAT_IN, null, orderLineItems, orderTable);

            assertThatIllegalStateException().isThrownBy(() -> orderService.create(order));
        }

        @DisplayName("주문 요청시의 주문 목록 가격과 현재 메뉴 가격은 같아야 한다")
        @Test
        void test12() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, -1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = createOrder(OrderType.EAT_IN, null, orderLineItems, orderTable);

            assertThatIllegalArgumentException().isThrownBy(() -> orderService.create(order));
        }
    }

    @DisplayName("주문 승인")
    @Nested
    class Accept {
        @DisplayName("요청일 때 주문 승인이 가능하다")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(OrderFixture.createDeliveryOrderWithDefaultId(UUID.randomUUID(), OrderStatus.WAITING, orderLineItems));

            final Order acceptedOrder = orderService.accept(order.getId());

            assertAll(
                    () -> assertThat(acceptedOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(acceptedOrder.getStatus()).isEqualTo(OrderStatus.ACCEPTED)
            );
        }

        @DisplayName("승인 될 주문은 주문 요청이 된 상태여야 한다")
        @EnumSource(value = OrderStatus.class, names = {"WAITING"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test2(final OrderStatus orderStatus) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(OrderFixture.createDeliveryOrderWithDefaultId(UUID.randomUUID(), orderStatus, orderLineItems));

            assertThatIllegalStateException().isThrownBy(() -> orderService.accept(order.getId()));
        }

        @DisplayName("주문 승인시 주문 수령 방법이 배달일 경우 라이더 배정은 필수이다")
        @Test
        void test3() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 3, BigDecimal.TEN));
            final Order order = orderRepository.save(OrderFixture.createDeliveryOrderWithDefaultId(UUID.randomUUID(), OrderStatus.WAITING, orderLineItems));

            doNothing().when(kitchenridersClient).requestDelivery(order.getId(), BigDecimal.valueOf(30), order.getDeliveryAddress());

            final Order acceptedOrder = orderService.accept(order.getId());

            assertAll(
                    () -> assertThat(acceptedOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(acceptedOrder.getStatus()).isEqualTo(OrderStatus.ACCEPTED)
            );
        }
    }

    @DisplayName("주문 제공")
    @Nested
    class Serve {
        @DisplayName("주문 제공이 가능하다")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 3, BigDecimal.TEN));
            final Order order = orderRepository.save(OrderFixture.createDeliveryOrderWithDefaultId(UUID.randomUUID(), OrderStatus.ACCEPTED, orderLineItems));

            final Order servedOrder = orderService.serve(order.getId());

            assertAll(
                    () -> assertThat(servedOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(servedOrder.getStatus()).isEqualTo(OrderStatus.SERVED)
            );
        }

        @DisplayName("제공 될 주문은 주문 승인이 된 상태여야 한다")
        @EnumSource(value = OrderStatus.class, names = {"ACCEPTED"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test2(final OrderStatus orderStatus) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(OrderFixture.createDeliveryOrderWithDefaultId(UUID.randomUUID(), orderStatus, orderLineItems));

            assertThatIllegalStateException().isThrownBy(() -> orderService.serve(order.getId()));
        }
    }

    @DisplayName("배달 시작")
    @Nested
    class StartDelivery {
        @DisplayName("주문 배달 시작이 가능하다.")
        @Test
        void test3() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(OrderFixture.createDeliveryOrderWithDefaultId(UUID.randomUUID(), OrderStatus.SERVED, orderLineItems));

            final Order startedDeliveryOrder = orderService.startDelivery(order.getId());

            assertAll(
                    () -> assertThat(startedDeliveryOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(startedDeliveryOrder.getStatus()).isEqualTo(OrderStatus.DELIVERING)
            );
        }

        @DisplayName("주문 배달 시작은 주문 수령 방법이 배달이어야 한다.")
        @EnumSource(value = OrderType.class, names = {"DELIVERY"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test4(final OrderType orderType) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), orderType, OrderStatus.SERVED, "청주시", orderLineItems, orderTable));

            assertThatIllegalStateException().isThrownBy(() -> orderService.startDelivery(order.getId()));
        }

        @DisplayName("주문 배달 시작은 주문 제공이 된 상태여야 한다.")
        @EnumSource(value = OrderStatus.class, names = {"SERVED"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test5(final OrderStatus orderStatus) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.DELIVERY, orderStatus, "청주시", orderLineItems, orderTable));

            assertThatIllegalStateException().isThrownBy(() -> orderService.startDelivery(order.getId()));
        }
    }

    @DisplayName("배달 완료")
    @Nested
    class CompleteDelivery {
        @DisplayName("주문 배달 완료가 가능하다.")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(OrderFixture.createDeliveryOrderWithDefaultId(UUID.randomUUID(), OrderStatus.DELIVERING, orderLineItems));

            final Order completedDeliveryOrder = orderService.completeDelivery(order.getId());

            assertAll(
                    () -> assertThat(completedDeliveryOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(completedDeliveryOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED)
            );
        }

        @DisplayName("주문 배달 완료는 주문 수령 방법이 배달이어야 한다.")
        @EnumSource(value = OrderType.class, names = {"DELIVERY"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test2(final OrderType orderType) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), orderType, OrderStatus.SERVED, "청주시", orderLineItems, orderTable));

            assertThatIllegalStateException().isThrownBy(() -> orderService.completeDelivery(order.getId()));
        }

        @DisplayName("주문 배달 시작은 배달중 상태여야 한다.")
        @EnumSource(value = OrderStatus.class, names = {"DELIVERING"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test3(final OrderStatus orderStatus) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, false));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.DELIVERY, orderStatus, "청주시", orderLineItems, orderTable));

            assertThatIllegalStateException().isThrownBy(() -> orderService.completeDelivery(order.getId()));
        }
    }

    @DisplayName("주문 종료(완료)")
    @Nested
    class Complete {
        @DisplayName("배달의 주문 종료시 상태는 배달됨 이어야 한다.")
        @Test
        void test4() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.DELIVERY, OrderStatus.DELIVERED, "청주시", orderLineItems, null));

            final Order completedOrder = orderService.complete(order.getId());

            assertAll(
                    () -> assertThat(completedOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED)
            );
        }

        @DisplayName("배달의 주문 종료시 상태는 배달됨이 아니면 실패한다")
        @EnumSource(value = OrderStatus.class, names = {"DELIVERED"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test5(final OrderStatus orderStatus) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.DELIVERY, orderStatus, "청주시", orderLineItems, null));

            assertThatIllegalStateException().isThrownBy(() -> orderService.complete(order.getId()));
        }

        @DisplayName("테이크 아웃 주문 종료시 상태는 제공됨 이어야 한다.")
        @Test
        void test6() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.TAKEOUT, OrderStatus.SERVED, "청주시", orderLineItems, null));

            final Order completedOrder = orderService.complete(order.getId());

            assertAll(
                    () -> assertThat(completedOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED)
            );
        }

        @DisplayName("테이크 아웃 주문 종료시 상태는 제공됨 아니면 실패한다")
        @EnumSource(value = OrderStatus.class, names = {"SERVED"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test7(final OrderStatus orderStatus) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.TAKEOUT, orderStatus, "청주시", orderLineItems, null));

            assertThatIllegalStateException().isThrownBy(() -> orderService.complete(order.getId()));
        }

        @DisplayName("매장 내 식사 주문 종료시 상태는 제공됨 이어야 한다.")
        @Test
        void test8() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, true));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.EAT_IN, OrderStatus.SERVED, "청주시", orderLineItems, orderTable));

            final Order completedOrder = orderService.complete(order.getId());

            assertAll(
                    () -> assertThat(completedOrder.getId()).isEqualTo(order.getId()),
                    () -> assertThat(completedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED),
                    () -> assertThat(completedOrder.getOrderTable().getNumberOfGuests()).isZero(),
                    () -> assertThat(completedOrder.getOrderTable().isOccupied()).isFalse()
            );
        }

        @DisplayName("매장 내 식사 주문 종료시 상태는 제공됨 아니면 실패한다")
        @EnumSource(value = OrderStatus.class, names = {"SERVED"}, mode = EnumSource.Mode.EXCLUDE)
        @ParameterizedTest
        void test9(final OrderStatus orderStatus) {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, true));
            final Order order = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.EAT_IN, orderStatus, null, orderLineItems, orderTable));

            assertThatIllegalStateException().isThrownBy(() -> orderService.complete(order.getId()));
        }
    }

    @DisplayName("전체 조회")
    @Nested
    class FindAll {
        @DisplayName("주문은 전체 조회가 가능하다")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), BigDecimal.ONE, menuGroup, true, List.of(menuProduct)));
            final List<OrderLineItem> orderLineItems = List.of(createOrderLineItem(menu, 1, BigDecimal.TEN));
            final OrderTable orderTable = orderTableRepository.save(createOrderTable(UUID.randomUUID(), 5, true));
            final Order order1 = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.DELIVERY, OrderStatus.DELIVERED, null, orderLineItems, null));
            final Order order2 = orderRepository.save(createOrder(UUID.randomUUID(), OrderType.EAT_IN, OrderStatus.SERVED, null, orderLineItems, orderTable));

            final List<Order> orders = orderService.findAll();

            assertThat(orders).containsExactly(order1, order2);
        }
    }

    private static class OrderLineItemFields {
        final Menu menu;
        final long quantity;

        public OrderLineItemFields(final OrderLineItem orderLineItem) {
            this.menu = orderLineItem.getMenu();
            this.quantity = orderLineItem.getQuantity();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final OrderLineItemFields that = (OrderLineItemFields) o;
            return quantity == that.quantity && Objects.equals(menu, that.menu);
        }

        @Override
        public int hashCode() {
            return Objects.hash(menu, quantity);
        }
    }
}