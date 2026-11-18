package com.pharmacy.stock.modules.order.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.finance.service.FinanceService;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentFormDto;
import com.pharmacy.stock.modules.order.dto.OrderAdjustmentLineDto;
import com.pharmacy.stock.modules.order.dto.OrderFormDto;
import com.pharmacy.stock.modules.order.dto.OrderLineFormDto;
import com.pharmacy.stock.modules.order.dto.OrderReceptionFormDto;
import com.pharmacy.stock.modules.order.dto.OrderReceptionLineDto;
import com.pharmacy.stock.modules.order.entity.Order;
import com.pharmacy.stock.modules.order.entity.OrderItem;
import com.pharmacy.stock.modules.order.entity.OrderStatus;
import com.pharmacy.stock.modules.order.repository.OrderRepository;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
import com.pharmacy.stock.modules.stock.service.StockService;
import com.pharmacy.stock.modules.supply.entity.SupplyRequest;
import com.pharmacy.stock.modules.supply.entity.SupplyRequestItem;
import com.pharmacy.stock.modules.supply.repository.SupplyRequestRepository;
import com.pharmacy.stock.modules.supply.service.SupplyRequestService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du circuit des commandes fournisseurs passees par la DAAS
 * (specification v1.2) : creation DAAS, validation DBI, transmission fournisseur,
 * reception au Depot Central.
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
    @Mock
    private FinanceService financeService;
    @Mock
    private SupplyRequestService supplyRequestService;
    @Mock
    private SupplyRequestRepository supplyRequestRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private StructureEntity daas;
    private StructureEntity dc;
    private StructureEntity fournisseur;
    private Product product;
    private User author;

    @BeforeEach
    void setUp() {
        daas = new StructureEntity(7L, "DAAS", "Division des Achats", "DAAS", true, null);
        dc = new StructureEntity(2L, "DC", "Dépôt Central", "DEPOT_CENTRAL", true, null);
        fournisseur = new StructureEntity(6L, "FRN01", "Fournisseur Principal", "FOURNISSEUR", true, null);
        product = new Product(1L, "MED001", "Paracétamol 500mg", "Boîte", BigDecimal.TEN, null);
        author = new User();
        author.setId(1L);
        author.setUsername("agent_daas");
        author.setFirstName("Ndiaye");
        author.setLastName("Sokhna");
    }

    private Order order(Long id, OrderStatus status, Integer quantityAdjusted, Integer quantityReceived) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("CMD-2026-0001");
        order.setSourceEntity(daas);
        order.setSupplierEntity(fournisseur);
        order.setReceivingEntity(dc);
        order.setStatus(status.name());
        order.setTotalAmount(BigDecimal.ZERO);
        order.setCreatedByUser(author);
        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantityRequested(40);
        item.setQuantityAdjusted(quantityAdjusted);
        item.setQuantityReceived(quantityReceived);
        item.setUnitPrice(BigDecimal.TEN);
        order.getOrderItems().add(item);
        return order;
    }

    private void stubOrder(Order order) {
        when(orderRepository.findByIdWithDetails(order.getId())).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(structureEntityRepository.findByType("DEPOT_CENTRAL")).thenReturn(List.of(dc));
    }

    // ------------------------------------------------------------------
    // Creation & soumission (DAAS uniquement)
    // ------------------------------------------------------------------

    @Test
    void creation_par_la_daas_produit_un_brouillon() {
        when(structureEntityRepository.findById(7L)).thenReturn(Optional.of(daas));
        when(structureEntityRepository.findById(6L)).thenReturn(Optional.of(fournisseur));
        when(structureEntityRepository.findByType("DEPOT_CENTRAL")).thenReturn(List.of(dc));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(orderRepository.findLastOrderNumberOfYear("2026")).thenReturn(null);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderFormDto form = new OrderFormDto();
        form.setSupplierEntityId(6L);
        form.getLines().add(new OrderLineFormDto(1L, 20));

        var created = orderService.createOrder(form, 1L, "DAAS", 7L);

        assertThat(created.getStatus()).isEqualTo(OrderStatus.BROUILLON.name());
        assertThat(created.getReceivingEntityId()).isEqualTo(2L);
        assertThat(created.getOrderNumber()).isEqualTo("CMD-2026-0001");
        // Prix unitaire valorise depuis le catalogue produit
        assertThat(created.getItems().get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void creation_par_un_role_autre_que_daas_est_rejetee() {
        OrderFormDto form = new OrderFormDto();
        form.setSupplierEntityId(6L);
        form.getLines().add(new OrderLineFormDto(1L, 20));

        assertThatThrownBy(() -> orderService.createOrder(form, 1L, "CHEF_SERVICE", 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Seule la DAAS");
    }

    @Test
    void creation_vers_un_non_fournisseur_est_rejetee() {
        when(structureEntityRepository.findById(7L)).thenReturn(Optional.of(daas));
        when(structureEntityRepository.findById(2L)).thenReturn(Optional.of(dc));

        OrderFormDto form = new OrderFormDto();
        form.setSupplierEntityId(2L);
        form.getLines().add(new OrderLineFormDto(1L, 20));

        assertThatThrownBy(() -> orderService.createOrder(form, 1L, "DAAS", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FOURNISSEUR");
    }

    @Test
    void soumission_va_en_attente_dbi() {
        Order order = order(1L, OrderStatus.BROUILLON, null, 0);
        stubOrder(order);

        var submitted = orderService.submitOrder(1L, 1L, "DAAS");

        assertThat(submitted.getStatus()).isEqualTo(OrderStatus.EN_ATTENTE_DBI.name());
    }

    @Test
    void soumission_par_un_autre_utilisateur_est_rejetee() {
        Order order = order(1L, OrderStatus.BROUILLON, null, 0);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.submitOrder(1L, 99L, "DAAS"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("l'auteur");
    }

    // ------------------------------------------------------------------
    // Décisions DBI
    // ------------------------------------------------------------------

    @Test
    void reajustement_dbi_puis_validation_enregistre_l_engagement_fournisseur() {
        Order order = order(1L, OrderStatus.EN_ATTENTE_DBI, null, 0);
        stubOrder(order);

        OrderAdjustmentFormDto form = new OrderAdjustmentFormDto();
        form.getAdjustments().add(new OrderAdjustmentLineDto(100L, 30));

        var adjusted = orderService.adjustOrder(1L, form, 1L, "DBI");
        assertThat(adjusted.getStatus()).isEqualTo(OrderStatus.AJUSTEE_DBI.name());
        assertThat(adjusted.getItems().get(0).getQuantityAdjusted()).isEqualTo(30);

        var validated = orderService.validateByDbi(1L, 1L, "DBI");
        assertThat(validated.getStatus()).isEqualTo(OrderStatus.VALIDEE_DBI.name());
        // Engagement : 30 x 10 = 300
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(financeService).recordSupplierEngagement(eq(1L), amountCaptor.capture(), eq(1L), any());
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("300");
    }

    @Test
    void reajustement_superieur_a_la_demande_est_rejete() {
        Order order = order(1L, OrderStatus.EN_ATTENTE_DBI, null, 0);
        stubOrder(order);

        OrderAdjustmentFormDto form = new OrderAdjustmentFormDto();
        form.getAdjustments().add(new OrderAdjustmentLineDto(100L, 50));

        assertThatThrownBy(() -> orderService.adjustOrder(1L, form, 1L, "DBI"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("supérieure à la quantité demandée");
    }

    @Test
    void validation_dbi_interdite_aux_autres_roles() {
        Order order = order(1L, OrderStatus.EN_ATTENTE_DBI, null, 0);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.validateByDbi(1L, 1L, "CHEF_SERVICE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DBI");
    }

    @Test
    void rejet_exige_un_motif() {
        Order order = order(1L, OrderStatus.EN_ATTENTE_DBI, null, 0);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.rejectOrder(1L, "  ", 1L, "DBI"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motif");

        var rejected = orderService.rejectOrder(1L, "Budget insuffisant", 1L, "DBI");
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJETEE.name());
        assertThat(rejected.getRejectionReason()).isEqualTo("Budget insuffisant");
    }

    // ------------------------------------------------------------------
    // Transmission fournisseur & réception Dépôt Central
    // ------------------------------------------------------------------

    @Test
    void transmission_impossible_sans_validation_dbi() {
        Order order = order(1L, OrderStatus.EN_ATTENTE_DBI, null, 0);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.sendToSupplier(1L, 1L, "DAAS"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Action non permise");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void transmission_par_la_daas_apres_validation() {
        Order order = order(1L, OrderStatus.VALIDEE_DBI, 30, 0);
        stubOrder(order);

        var sent = orderService.sendToSupplier(1L, 1L, "DAAS");

        assertThat(sent.getStatus()).isEqualTo(OrderStatus.ENVOYEE_FOURNISSEUR.name());
        assertThat(sent.getSentAt()).isNotNull();
    }

    @Test
    void reception_partielle_puis_complete_credite_le_stock_du_depot() {
        Order order = order(1L, OrderStatus.ENVOYEE_FOURNISSEUR, 30, 0);
        stubOrder(order);

        OrderReceptionFormDto form = new OrderReceptionFormDto();
        form.getReceptions().add(new OrderReceptionLineDto(100L, 20));

        var partial = orderService.receiveOrder(1L, form, 1L, "RESPONSABLE_DEPOT", 2L);
        assertThat(partial.getStatus()).isEqualTo(OrderStatus.RECUE_PARTIELLEMENT.name());
        assertThat(partial.getItems().get(0).getQuantityReceived()).isEqualTo(20);

        OrderReceptionFormDto form2 = new OrderReceptionFormDto();
        form2.getReceptions().add(new OrderReceptionLineDto(100L, 10));
        var complete = orderService.receiveOrder(1L, form2, 1L, "RESPONSABLE_DEPOT", 2L);
        assertThat(complete.getStatus()).isEqualTo(OrderStatus.RECUE.name());

        ArgumentCaptor<StockMovementFormDto> movementCaptor = ArgumentCaptor.forClass(StockMovementFormDto.class);
        verify(stockService, org.mockito.Mockito.times(2))
                .recordMovement(movementCaptor.capture(), any(), isNull(), eq(1L));
        StockMovementFormDto movement = movementCaptor.getValue();
        assertThat(movement.getEntityId()).isEqualTo(2L);
        assertThat(movement.getType()).isEqualTo("ENTREE");
        assertThat(movement.getQuantity()).isEqualTo(10);
    }

    @Test
    void reception_superieure_au_reste_est_rejetee() {
        Order order = order(1L, OrderStatus.ENVOYEE_FOURNISSEUR, 30, 0);
        stubOrder(order);

        OrderReceptionFormDto form = new OrderReceptionFormDto();
        form.getReceptions().add(new OrderReceptionLineDto(100L, 40));

        assertThatThrownBy(() -> orderService.receiveOrder(1L, form, 1L, "RESPONSABLE_DEPOT", 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reste à recevoir");
    }

    @Test
    void reception_par_une_autre_structure_est_rejetee() {
        Order order = order(1L, OrderStatus.ENVOYEE_FOURNISSEUR, 30, 0);
        stubOrder(order);

        assertThatThrownBy(() -> orderService.receiveOrder(1L, null, 1L, "RESPONSABLE_DEPOT", 4L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Dépôt Central");
    }

    @Test
    void reception_complete_met_a_jour_la_demande_liee() {
        Order order = order(1L, OrderStatus.ENVOYEE_FOURNISSEUR, 30, 0);
        SupplyRequest request = new SupplyRequest();
        request.setId(9L);
        request.setRequestNumber("DEM-2026-0001");
        SupplyRequestItem requestItem = new SupplyRequestItem();
        requestItem.setProduct(product);
        requestItem.setQuantityRequested(30);
        requestItem.setQuantityDelivered(0);
        request.getRequestItems().add(requestItem);
        order.setSupplyRequest(request);
        stubOrder(order);

        OrderReceptionFormDto form = new OrderReceptionFormDto();
        form.getReceptions().add(new OrderReceptionLineDto(100L, 30));
        orderService.receiveOrder(1L, form, 1L, "RESPONSABLE_DEPOT", 2L);

        verify(supplyRequestService).registerOrderDelivery(any(Order.class));
    }

    // ------------------------------------------------------------------
    // Commande liée à une demande DC -> DAAS
    // ------------------------------------------------------------------

    @Test
    void creation_liee_a_une_demande_dc_daas_passe_la_demande_en_cours() {
        when(structureEntityRepository.findById(7L)).thenReturn(Optional.of(daas));
        when(structureEntityRepository.findById(6L)).thenReturn(Optional.of(fournisseur));
        when(structureEntityRepository.findByType("DEPOT_CENTRAL")).thenReturn(List.of(dc));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(orderRepository.findLastOrderNumberOfYear("2026")).thenReturn(null);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.existsBySupplyRequestId(9L)).thenReturn(false);

        SupplyRequest request = new SupplyRequest();
        request.setId(9L);
        request.setRequestNumber("DEM-2026-0001");
        request.setStatus("SOUMISE");
        request.setSupplyingEntity(daas);
        request.setRequestingEntity(dc);
        SupplyRequestItem item = new SupplyRequestItem();
        item.setProduct(product);
        item.setQuantityRequested(25);
        item.setQuantityDelivered(0);
        request.getRequestItems().add(item);
        when(supplyRequestRepository.findById(9L)).thenReturn(Optional.of(request));

        OrderFormDto form = new OrderFormDto();
        form.setSupplierEntityId(6L);
        form.setSupplyRequestId(9L);
        // Aucune ligne : preremplissage depuis la demande

        var created = orderService.createOrder(form, 1L, "DAAS", 7L);

        assertThat(created.getItems()).hasSize(1);
        assertThat(created.getItems().get(0).getQuantityRequested()).isEqualTo(25);
        verify(supplyRequestService).markProcessingByDaas(eq(9L), anyLong());
    }

    @Test
    void creation_liee_a_une_demande_deja_traitee_est_rejetee() {
        when(structureEntityRepository.findById(7L)).thenReturn(Optional.of(daas));
        when(structureEntityRepository.findById(6L)).thenReturn(Optional.of(fournisseur));
        when(structureEntityRepository.findByType("DEPOT_CENTRAL")).thenReturn(List.of(dc));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.findLastOrderNumberOfYear("2026")).thenReturn(null);
        when(orderRepository.existsBySupplyRequestId(9L)).thenReturn(true);

        SupplyRequest request = new SupplyRequest();
        request.setId(9L);
        request.setStatus("EN_COURS_TRAITEMENT");
        request.setSupplyingEntity(daas);
        request.setRequestingEntity(dc);
        when(supplyRequestRepository.findById(9L)).thenReturn(Optional.of(request));

        OrderFormDto form = new OrderFormDto();
        form.setSupplierEntityId(6L);
        form.setSupplyRequestId(9L);
        form.getLines().add(new OrderLineFormDto(1L, 20));

        assertThatThrownBy(() -> orderService.createOrder(form, 1L, "DAAS", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà liée");
    }
}
