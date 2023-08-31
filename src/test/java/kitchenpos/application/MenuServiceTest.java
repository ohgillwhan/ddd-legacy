package kitchenpos.application;

import kitchenpos.domain.*;
import kitchenpos.fixture.MenuProductFixture;
import kitchenpos.infra.PurgomalumClient;
import kitchenpos.support.BaseServiceTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static kitchenpos.fixture.MenuFixture.createMenu;
import static kitchenpos.fixture.MenuGroupFixture.createMenuGroup;
import static kitchenpos.fixture.MenuProductFixture.createMenuProductWithDefaultId;
import static kitchenpos.fixture.ProductFixture.createProduct;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

class MenuServiceTest extends BaseServiceTest {
    private final MenuService menuService;
    private final MenuRepository menuRepository;
    private final MenuGroupRepository menuGroupRepository;
    private final ProductRepository productRepository;

    @MockBean
    private PurgomalumClient purgomalumClient;

    public MenuServiceTest(final MenuService menuService, final MenuRepository menuRepository, final MenuGroupRepository menuGroupRepository, final ProductRepository productRepository) {
        this.menuService = menuService;
        this.menuRepository = menuRepository;
        this.menuGroupRepository = menuGroupRepository;
        this.productRepository = productRepository;
    }

    @DisplayName("등록")
    @Nested
    class Create {
        @DisplayName("메뉴는 등록이 가능하다")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = createMenu(menuGroup, List.of(menuProduct));
            given(purgomalumClient.containsProfanity(menu.getName())).willReturn(false);

            final Menu createdMenu = menuService.create(menu);

            final Menu foundMenu = menuRepository.findAll().get(0);

            assertAll(
                    () -> assertThat(createdMenu.getId()).isNotNull(),
                    () -> assertThat(createdMenu.getName()).isEqualTo(menu.getName()),
                    () -> assertThat(createdMenu.getPrice()).isEqualTo(menu.getPrice()),
                    () -> assertThat(createdMenu.getMenuGroup()).isEqualTo(menuGroup),
                    () -> assertThat(createdMenu.isDisplayed()).isEqualTo(menu.isDisplayed()),
                    () -> assertThat(createdMenu.getMenuProducts())
                            .map(MenuProductFields::new)
                            .containsExactlyElementsOf(menu.getMenuProducts().stream().map(MenuProductFields::new).collect(Collectors.toList())),
                    () -> assertThat(foundMenu.getId()).isEqualTo(createdMenu.getId())
            );
        }

        @DisplayName("메뉴의 이름은 비어 있으면 안된다")
        @Test
        void test2() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = createMenu(null, BigDecimal.ONE, menuGroup, List.of(menuProduct));
            given(purgomalumClient.containsProfanity(menu.getName())).willReturn(false);

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴의 이름은 비속어가 포함되면 안된다")
        @Test
        void test3() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = createMenu("비속어", BigDecimal.ONE, menuGroup, List.of(menuProduct));

            when(purgomalumClient.containsProfanity(menu.getName())).thenReturn(true);

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴의 가격은 비어 있으면 안된다")
        @Test
        void test4() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = createMenu("치킨", null, menuGroup, List.of(menuProduct));

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴의 가격은 0원 이상이어야 한다")
        @Test
        void test5() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = createMenu("치킨", new BigDecimal("-0.0001"), menuGroup, List.of(menuProduct));

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴는 연관 메뉴에 필수적으로 등록 되어야 한다")
        @Test
        void test6() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = createMenu(menuGroup, List.of(menuProduct), UUID.randomUUID());

            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴는 메뉴 구성이 비어있으면 안된다")
        @Test
        void test7() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Menu menu = createMenu(menuGroup, null);

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴는 하나 이상의 상품으로 구성이 되어야 한다")
        @Test
        void test8() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Menu menu = createMenu(menuGroup, Collections.emptyList());

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴의 메뉴 구성의 상품 수량은 0개 이상이어야 한다")
        @Test
        void test9() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID()));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product, -1);
            final Menu menu = createMenu(menuGroup, List.of(menuProduct));

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }

        @DisplayName("메뉴는 하나 이상의 상품으로 구성이 되어야 한다")
        @Test
        void test10() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.TEN));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = createMenu("치킨", new BigDecimal(1000000000), menuGroup, List.of(menuProduct));

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.create(menu));
        }
    }

    @DisplayName("가격 수정")
    @Nested
    class ChangePrice {
        @DisplayName("메뉴는 가격 수정이 가능하다.")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product chicken = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.ONE));
            final Product pizza = productRepository.save(createProduct(UUID.randomUUID(), "피자", BigDecimal.ONE));
            final MenuProduct chickenMenuProduct = MenuProductFixture.createMenuProduct(null, chicken, 1);
            final MenuProduct pizzaMenuProduct = MenuProductFixture.createMenuProduct(null, pizza, 1);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), "치킨피자", BigDecimal.TEN, menuGroup, List.of(chickenMenuProduct, pizzaMenuProduct)));
            final Menu changeMenu = createMenu(BigDecimal.ONE);

            final Menu changedMenu = menuService.changePrice(menu.getId(), changeMenu);

            assertAll(
                    () -> assertThat(changedMenu.getId()).isEqualTo(menu.getId()),
                    () -> assertThat(changedMenu.getPrice()).isEqualTo(changeMenu.getPrice())
            );
        }

        @Disabled
        @DisplayName("메뉴는 가격 수정이 가능하다. - 프로덕션 코드의 버그로 인한 리팩터링 필요")
        @Test
        void test1_have_to_refactor() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product chicken = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.ONE));
            final Product pizza = productRepository.save(createProduct(UUID.randomUUID(), "피자", BigDecimal.ONE));
            final MenuProduct chickenMenuProduct = MenuProductFixture.createMenuProduct(null, chicken, 1);
            final MenuProduct pizzaMenuProduct = MenuProductFixture.createMenuProduct(null, pizza, 1);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), "치킨피자", new BigDecimal(2), menuGroup, List.of(chickenMenuProduct, pizzaMenuProduct)));
            final Menu changeMenu = createMenu(new BigDecimal(2));

            final Menu changedMenu = menuService.changePrice(menu.getId(), changeMenu);

            assertAll(
                    () -> assertThat(changedMenu.getId()).isEqualTo(menu.getId()),
                    () -> assertThat(changedMenu.getPrice()).isEqualTo(changeMenu.getPrice())
            );
        }

        @DisplayName("수정 될 메뉴의 가격 0원 이상이어야 한다.")
        @Test
        void test2() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.TEN));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), "치킨", BigDecimal.ONE, menuGroup, List.of(menuProduct)));
            final Menu changeMenu = createMenu(new BigDecimal("-0.0001"));

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.changePrice(menu.getId(), changeMenu));
        }

        @DisplayName("메뉴의 가격 수정시 메뉴의 가격은 모든 메뉴 구성의 합보다 크면 안된다")
        @Test
        void test3() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.ONE));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), "치킨", BigDecimal.TEN, menuGroup, List.of(menuProduct)));
            final Menu changeMenu = createMenu(new BigDecimal(100000000));

            assertThatIllegalArgumentException().isThrownBy(() -> menuService.changePrice(menu.getId(), changeMenu));
        }
    }

    @DisplayName("표출")
    @Nested
    class Display {
        @DisplayName("메뉴는 키오스크에 표출할 수 있다.")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.TEN));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, false, List.of(menuProduct)));

            final Menu changedMenu = menuService.display(menu.getId());

            assertAll(
                    () -> assertThat(changedMenu.getId()).isEqualTo(menu.getId()),
                    () -> assertThat(changedMenu.isDisplayed()).isTrue()
            );
        }

        @DisplayName("메뉴의 가격 수정시 메뉴의 가격은 모든 메뉴 구성의 합보다 크면 안된다")
        @Test
        void test2() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.TEN));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), "치킨", new BigDecimal(100000000), menuGroup, List.of(menuProduct)));

            assertThatIllegalStateException().isThrownBy(() -> menuService.display(menu.getId()));
        }
    }
    @DisplayName("미표출")
    @Nested
    class Hide {
        @DisplayName("메뉴는 키오스크에 미표출할 수 있다.")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.TEN));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));

            final Menu changedMenu = menuService.hide(menu.getId());

            assertAll(
                    () -> assertThat(changedMenu.getId()).isEqualTo(menu.getId()),
                    () -> assertThat(changedMenu.isDisplayed()).isFalse()
            );
        }
    }

    @DisplayName("전체 조회")
    @Nested
    class FindAll {
        @DisplayName("메뉴는 전체 조회가 가능하다.")
        @Test
        void test1() {
            final MenuGroup menuGroup = menuGroupRepository.save(createMenuGroup(UUID.randomUUID()));
            final Product product = productRepository.save(createProduct(UUID.randomUUID(), "치킨", BigDecimal.TEN));
            final MenuProduct menuProduct = MenuProductFixture.createMenuProductWithDefaultId(product);
            final Menu menu1 = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, true, List.of(menuProduct)));
            final Menu menu2 = menuRepository.save(createMenu(UUID.randomUUID(), menuGroup, false, List.of(menuProduct)));

            final List<Menu> menus = menuService.findAll();

            assertThat(menus).contains(menu1, menu2);
        }
    }

    private static class MenuProductFields {
        final Product product;
        final long quantity;

        public MenuProductFields(MenuProduct menuProduct) {
            this.product = menuProduct.getProduct();
            this.quantity = menuProduct.getQuantity();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final MenuProductFields that = (MenuProductFields) o;
            return quantity == that.quantity && Objects.equals(product, that.product);
        }

        @Override
        public int hashCode() {
            return Objects.hash(product, quantity);
        }
    }
}