package com.pharmacy.stock.modules.stock.service;

import com.pharmacy.stock.common.exception.BusinessException;
import com.pharmacy.stock.modules.entity.entity.StructureEntity;
import com.pharmacy.stock.modules.entity.repository.StructureEntityRepository;
import com.pharmacy.stock.modules.product.entity.Product;
import com.pharmacy.stock.modules.product.repository.ProductRepository;
import com.pharmacy.stock.modules.stock.dto.StockMovementFormDto;
import com.pharmacy.stock.modules.stock.dto.StockThresholdFormDto;
import com.pharmacy.stock.modules.stock.entity.Stock;
import com.pharmacy.stock.modules.stock.entity.StockMovement;
import com.pharmacy.stock.modules.stock.repository.StockMovementRepository;
import com.pharmacy.stock.modules.stock.repository.StockRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du service de gestion des stocks (mouvements, ajustements, seuils).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceImplTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private StructureEntityRepository structureEntityRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StockServiceImpl stockService;

    private StructureEntity entity;
    private Product product;
    private User user;

    @BeforeEach
    void setUp() {
        entity = new StructureEntity(1L, "PC", "Pharmacie Centrale", "PHARMACIE_CENTRALE", true, null);
        product = new Product(1L, "MED001", "Paracétamol 500mg", "Boîte", BigDecimal.TEN, null);
        user = new User();
        user.setId(1L);
        user.setUsername("admin");
    }

    private Stock stock(int quantity, int minThreshold) {
        Stock stock = new Stock();
        stock.setId(10L);
        stock.setEntity(entity);
        stock.setProduct(product);
        stock.setQuantity(quantity);
        stock.setMinThreshold(minThreshold);
        return stock;
    }

    private void stubReferences() {
        when(structureEntityRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void entree_cree_la_ligne_de_stock_si_absente() {
        stubReferences();
        when(stockRepository.findByEntityIdAndProductId(1L, 1L)).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        var movement = stockService.recordMovement(new StockMovementFormDto(1L, 1L, "ENTREE", 100), 1L);

        assertThat(movement.getQuantity()).isEqualTo(100);
        assertThat(movement.getStockBefore()).isZero();
        assertThat(movement.getStockAfter()).isEqualTo(100);

        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getQuantity()).isEqualTo(100);
        assertThat(stockCaptor.getValue().getMinThreshold()).isEqualTo(10);
    }

    @Test
    void sortie_valide_debite_le_stock() {
        stubReferences();
        when(stockRepository.findByEntityIdAndProductId(1L, 1L)).thenReturn(Optional.of(stock(20, 10)));
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        var movement = stockService.recordMovement(new StockMovementFormDto(1L, 1L, "SORTIE", 15), 1L);

        assertThat(movement.getQuantity()).isEqualTo(15);
        assertThat(movement.getStockBefore()).isEqualTo(20);
        assertThat(movement.getStockAfter()).isEqualTo(5);
    }

    @Test
    void sortie_superieure_au_stock_est_rejetee() {
        stubReferences();
        when(stockRepository.findByEntityIdAndProductId(1L, 1L)).thenReturn(Optional.of(stock(20, 10)));

        assertThatThrownBy(() -> stockService.recordMovement(new StockMovementFormDto(1L, 1L, "SORTIE", 50), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuffisant");
    }

    @Test
    void ajustement_inventaire_journalise_lecart() {
        stubReferences();
        when(stockRepository.findByEntityIdAndProductId(1L, 1L)).thenReturn(Optional.of(stock(20, 10)));
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));

        // Comptage physique : 12 unites -> ecart de -8
        var movement = stockService.recordMovement(new StockMovementFormDto(1L, 1L, "AJUSTEMENT_INVENTAIRE", 12), 1L);

        assertThat(movement.getStockAfter()).isEqualTo(12);
        assertThat(movement.getQuantity()).isEqualTo(-8);
    }

    @Test
    void type_de_mouvement_invalide_est_rejete() {
        assertThatThrownBy(() -> stockService.recordMovement(new StockMovementFormDto(1L, 1L, "MAUVAIS_TYPE", 10), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Type de mouvement invalide");
    }

    @Test
    void mise_a_jour_du_seuil_dalerte() {
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock(50, 10)));
        when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));

        StockThresholdFormDto form = new StockThresholdFormDto();
        form.setMinThreshold(25);

        var updated = stockService.updateThreshold(10L, form);

        assertThat(updated.getMinThreshold()).isEqualTo(25);
        assertThat(updated.isLowStock()).isFalse();
    }
}
