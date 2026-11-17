package com.pharmacy.stock.modules.order.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentLineDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;
import com.pharmacy.stock.modules.order.dto.OrderLineFormDto;
import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.order.entity.OrderItem;
import com.pharmacy.stock.modules.order.entity.OrderStatus;
import com.pharmacy.stock.modules.order.repository.OrderRepository;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
import com.pharmacy.stock.modules.stock.entity.Stock;
import com.pharmacy.stock.modules.stock.service.StockService;
import com.pharmacy.stock.modules.user.entity.User;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du circuit de validation des commandes (processus A et B).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StructureEntityRepository structureEntityRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockService stockService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private StructureEntity cm01;
    private StructureEntity pc;
    private StructureEntity dc;
    private StructureEntity fournisseur;
    private Product product;
    private User author;

    @BeforeEach
    void setUp() {
        cm01 = new StructureEntity(4L, "CM01", "Centre Médical 1", "CENTRE_MEDICAL", true, null);
        pc = new StructureEntity(1L, "PC", "Pharmacie Centrale", "PHARMACIE_CENTRALE", true, null);
        dc = new StructureEntity(2L, "DC", "Dépôt Central", "DEPOT_CENTRAL", true, null);
        fournisseur = new StructureEntity(6L, "FRN01", "Fournisseur Principal", "FOURNISSEUR", true, null);
        product = new Product(1L, "MED001", "Paracétamol 500mg", "Boîte", BigDecimal.TEN, null);
        author = new User();
        author.setId(1L);
        author.setUsername("chef_cm01");
        author.setFirstName("Diallo");
        author.setLastName("Fatou");
    }

    private Order order(Long id, StructureEntity source, StructureEntity target, OrderStatus status,
                        boolean requiresDbi, Integer quantityAdjusted) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("CMD-2026-0001");
        order.setSourceEntity(source);
        order.setTargetEntity(target);
        order.setStatus(status.name());
        order.setRequiresDbiValidation(requiresDbi);
        order.setCreatedByUser(author);
        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantityRequested(40);
        item.setQuantityAdjusted(quantityAdjusted);
        item.setQuantityDelivered(0);
        order.getOrderItems().add(item);
        return order;
    }

    private void stubOrder(Order order) {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
    }

    private Stock stock(int quantity) {
        Stock stock = new Stock();
        stock.setQuantity(quantity);
        return stock;
    }

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    @Test
    void creation_cm_vers_pc_sans_validation_dbi() {
        when(structureEntityRepository.findById(4L)).thenReturn(Optional.of(cm01));
        when(structureEntityRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(orderRepository.findLastOrderNumberOfYear("2026")).thenReturn(null);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderFormDto form = new OrderFormDto();
        form.setTargetEntityId(1L);
        form.getLines().add(new OrderLineFormDto(1L, 20));

        var created = orderService.createOrder(form, 1L, "CHEF_SERVICE", 4L);

        assertThat(created.getStatus()).isEqualTo(OrderStatus.BROUILLON.name());
        assertThat(created.getRequiresDbiValidation()).isFalse();
        assertThat(created.getOrderNumber()).isEqualTo("CMD-2026-0001");
    }

    @Test
    void creation_pc_vers_dci_requiert_la_validation_dbi() {
        when(structureEntityRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(structureEntityRepository.findById(2L)).thenReturn(Optional.of(dc));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(orderRepository.findLastOrderNumberOfYear("2026")).thenReturn("CMD-2026-0007");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderFormDto form = new OrderFormDto();
        form.setTargetEntityId(2L);
        form.getLines().add(new OrderLineFormDto(1L, 50));

        var created = orderService.createOrder(form, 1L, "RESPONSABLE_PHARMACIE", 1L);

        assertThat(created.getRequiresDbiValidation()).isTrue();
        assertThat(created.getOrderNumber()).isEqualTo("CMD-2026-0008");
    }

    @Test
    void creation_par_un_cm_vers_le_depot_est_rejetee() {
        when(structureEntityRepository.findById(4L)).thenReturn(Optional.of(cm01));
        when(structureEntityRepository.findById(2L)).thenReturn(Optional.of(dc));

        OrderFormDto form = new OrderFormDto();
        form.setTargetEntityId(2L);
        form.getLines().add(new OrderLineFormDto(1L, 20));

        assertThatThrownBy(() -> orderService.createOrder(form, 1L, "CHEF_SERVICE", 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pas autorisé");
    }

    @Test
    void creation_sans_ligne_est_rejetee() {
        when(structureEntityRepository.findById(4L)).thenReturn(Optional.of(cm01));
        when(structureEntityRepository.findById(1L)).thenReturn(Optional.of(pc));

        OrderFormDto form = new OrderFormDto();
        form.setTargetEntityId(1L);

        assertThatThrownBy(() -> orderService.createOrder(form, 1L, "CHEF_SERVICE", 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("au moins une ligne");
    }

    @Test
    void creation_avec_produit_en_double_est_rejetee() {
        when(structureEntityRepository.findById(4L)).thenReturn(Optional.of(cm01));
        when(structureEntityRepository.findById(1L)).thenReturn(Optional.of(pc));

        OrderFormDto form = new OrderFormDto();
        form.setTargetEntityId(1L);
        form.getLines().add(new OrderLineFormDto(1L, 20));
        form.getLines().add(new OrderLineFormDto(1L, 5));

        assertThatThrownBy(() -> orderService.createOrder(form, 1L, "CHEF_SERVICE", 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deux fois");
    }

    // ------------------------------------------------------------------
    // Soumission
    // ------------------------------------------------------------------

    @Test
    void soumission_cm_pc_va_en_attente_de_service() {
        Order order = order(1L, cm01, pc, OrderStatus.BROUILLON, false, null);
        stubOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var submitted = orderService.submitOrder(1L, 1L, "CHEF_SERVICE");

        assertThat(submitted.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_SERVIE.name());
    }

    @Test
    void soumission_pc_dci_va_en_attente_dbi() {
        Order order = order(1L, pc, dc, OrderStatus.BROUILLON, true, null);
        stubOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var submitted = orderService.submitOrder(1L, 1L, "RESPONSABLE_PHARMACIE");

        assertThat(submitted.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_DBI.name());
    }

    @Test
    void soumission_par_un_autre_utilisateur_est_rejetee() {
        Order order = order(1L, cm01, pc, OrderStatus.BROUILLON, false, null);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.submitOrder(1L, 99L, "CHEF_SERVICE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("l'auteur");
    }

    // ------------------------------------------------------------------
    // Processus A : livraison directe
    // ------------------------------------------------------------------

    @Test
    void livraison_directe_debite_le_stock_de_la_pc() {
        Order order = order(1L, cm01, pc, OrderStatus.EN_ATTENTE_SERVIE, false, null);
        stubOrder(order);
        when(stockRepository.findByEntityIdAndProductId(1L, 1L)).thenReturn(Optional.of(stock(50)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var delivered = orderService.deliverOrder(1L, 1L, "RESPONSABLE_PHARMACIE");

        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.LIVREE.name());
        assertThat(delivered.getItems().get(0).getQuantityDelivered()).isEqualTo(40);

        ArgumentCaptor<StockMovementFormDto> movementCaptor = ArgumentCaptor.forClass(StockMovementFormDto.class);
        verify(stockService).recordMovement(movementCaptor.capture(), any());
        assertThat(movementCaptor.getValue().getEntityId()).isEqualTo(1L);
        assertThat(movementCaptor.getValue().getProductId()).isEqualTo(1L);
        assertThat(movementCaptor.getValue().getType()).isEqualTo("SORTIE");
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(40);
    }

    @Test
    void livraison_directe_impossible_si_stock_insuffisant() {
        Order order = order(1L, cm01, pc, OrderStatus.EN_ATTENTE_SERVIE, false, null);
        stubOrder(order);
        when(stockRepository.findByEntityIdAndProductId(1L, 1L)).thenReturn(Optional.of(stock(10)));

        assertThatThrownBy(() -> orderService.deliverOrder(1L, 1L, "RESPONSABLE_PHARMACIE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuffisant");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void livraison_directe_interdite_au_circuit_dbi() {
        Order order = order(1L, pc, dc, OrderStatus.EN_ATTENTE_DBI, true, null);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.deliverOrder(1L, 1L, "RESPONSABLE_PHARMACIE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Action non permise");
    }

    // ------------------------------------------------------------------
    // Processus B : circuit DBI
    // ------------------------------------------------------------------

    @Test
    void reajustement_dbi_puis_validation() {
        Order order = order(1L, pc, dc, OrderStatus.EN_ATTENTE_DBI, true, null);
        stubOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderAdjustmentFormDto form = new OrderAdjustmentFormDto();
        form.getAdjustments().add(new OrderAdjustmentLineDto(100L, 30));

        var adjusted = orderService.adjustOrder(1L, form, 1L, "DBI");
        assertThat(adjusted.getStatus()).isEqualTo(OrderStatus.AJUSTEE_DBI.name());
        assertThat(adjusted.getItems().get(0).getQuantityAdjusted()).isEqualTo(30);

        var validated = orderService.validateByDbi(1L, 1L, "DBI");
        assertThat(validated.getStatus()).isEqualTo(OrderStatus.VALIDEE_DBI.name());
    }

    @Test
    void reajustement_superieur_a_la_demande_est_rejete() {
        Order order = order(1L, pc, dc, OrderStatus.EN_ATTENTE_DBI, true, null);
        stubOrder(order);

        OrderAdjustmentFormDto form = new OrderAdjustmentFormDto();
        form.getAdjustments().add(new OrderAdjustmentLineDto(100L, 50));

        assertThatThrownBy(() -> orderService.adjustOrder(1L, form, 1L, "DBI"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("supérieure à la quantité demandée");
    }

    @Test
    void validation_dbi_interdite_aux_autres_roles() {
        Order order = order(1L, pc, dc, OrderStatus.EN_ATTENTE_DBI, true, null);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.validateByDbi(1L, 1L, "CHEF_SERVICE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DBI");
    }

    @Test
    void expedition_debite_le_stock_du_depot_avec_les_quantites_ajustees() {
        Order order = order(1L, pc, dc, OrderStatus.VALIDEE_DBI, true, 30);
        stubOrder(order);
        when(stockRepository.findByEntityIdAndProductId(2L, 1L)).thenReturn(Optional.of(stock(100)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var shipped = orderService.shipOrder(1L, 1L, "RESPONSABLE_DEPOT");

        assertThat(shipped.getStatus()).isEqualTo(OrderStatus.EXPEDIEE.name());
        assertThat(shipped.getItems().get(0).getQuantityDelivered()).isEqualTo(30);

        ArgumentCaptor<StockMovementFormDto> movementCaptor = ArgumentCaptor.forClass(StockMovementFormDto.class);
        verify(stockService).recordMovement(movementCaptor.capture(), any());
        assertThat(movementCaptor.getValue().getEntityId()).isEqualTo(2L);
        assertThat(movementCaptor.getValue().getType()).isEqualTo("SORTIE");
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(30);
    }

    @Test
    void expedition_impossible_si_stock_depot_insuffisant() {
        Order order = order(1L, pc, dc, OrderStatus.VALIDEE_DBI, true, 30);
        stubOrder(order);
        when(stockRepository.findByEntityIdAndProductId(2L, 1L)).thenReturn(Optional.of(stock(5)));

        assertThatThrownBy(() -> orderService.shipOrder(1L, 1L, "RESPONSABLE_DEPOT"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuffisant");
    }

    @Test
    void reception_de_lexpedition_credite_le_stock_de_lemetteur() {
        Order order = order(1L, pc, dc, OrderStatus.EXPEDIEE, true, null);
        order.getOrderItems().get(0).setQuantityDelivered(30);
        stubOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var received = orderService.receiveOrder(1L, 1L, "RESPONSABLE_PHARMACIE", 1L);

        assertThat(received.getStatus()).isEqualTo(OrderStatus.LIVREE.name());

        ArgumentCaptor<StockMovementFormDto> movementCaptor = ArgumentCaptor.forClass(StockMovementFormDto.class);
        verify(stockService).recordMovement(movementCaptor.capture(), any());
        assertThat(movementCaptor.getValue().getEntityId()).isEqualTo(1L);
        assertThat(movementCaptor.getValue().getType()).isEqualTo("ENTREE");
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(30);
    }

    @Test
    void reception_fournisseur_credite_le_stock_du_depot() {
        Order order = order(1L, dc, fournisseur, OrderStatus.VALIDEE_DBI, true, 200);
        stubOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var received = orderService.receiveSupplierDelivery(1L, 1L, "RESPONSABLE_DEPOT");

        assertThat(received.getStatus()).isEqualTo(OrderStatus.LIVREE.name());
        assertThat(received.getItems().get(0).getQuantityDelivered()).isEqualTo(200);

        ArgumentCaptor<StockMovementFormDto> movementCaptor = ArgumentCaptor.forClass(StockMovementFormDto.class);
        verify(stockService).recordMovement(movementCaptor.capture(), any());
        assertThat(movementCaptor.getValue().getEntityId()).isEqualTo(2L);
        assertThat(movementCaptor.getValue().getType()).isEqualTo("ENTREE");
        assertThat(movementCaptor.getValue().getQuantity()).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Rejet
    // ------------------------------------------------------------------

    @Test
    void rejet_dune_demande_directe_par_la_pc() {
        Order order = order(1L, cm01, pc, OrderStatus.EN_ATTENTE_SERVIE, false, null);
        stubOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var rejected = orderService.rejectOrder(1L, 1L, "RESPONSABLE_PHARMACIE");

        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJETEE.name());
    }

    @Test
    void rejet_dune_commande_dbi_par_la_dbi() {
        Order order = order(1L, pc, dc, OrderStatus.AJUSTEE_DBI, true, 10);
        stubOrder(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var rejected = orderService.rejectOrder(1L, 1L, "DBI");

        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJETEE.name());
    }

    @Test
    void rejet_impossible_sur_une_commande_brouillon() {
        Order order = order(1L, cm01, pc, OrderStatus.BROUILLON, false, null);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.rejectOrder(1L, 1L, "ADMIN"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Impossible de rejeter");
    }
}
