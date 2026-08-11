package net.austizz.ultimatebankingsystem.api;

import net.austizz.ultimatebankingsystem.api.bank.UltimateBankManagementApi;
import net.austizz.ultimatebankingsystem.api.bank.UltimateBankManagementApiImpl;
import net.austizz.ultimatebankingsystem.api.general.UltimateServerApi;
import net.austizz.ultimatebankingsystem.api.general.UltimateServerApiImpl;
import net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApi;
import net.austizz.ultimatebankingsystem.api.economy.UltimateEconomyApiImpl;
import net.austizz.ultimatebankingsystem.api.heist.UltimateHeistApi;
import net.austizz.ultimatebankingsystem.api.heist.UltimateHeistApiImpl;
import net.austizz.ultimatebankingsystem.api.shop.UltimateShopManagementApi;
import net.austizz.ultimatebankingsystem.api.shop.UltimateShopManagementApiImpl;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("1.1.0")
public final class UltimateBankingApiProvider {
    private static final UltimateBankingApi INSTANCE = new UltimateBankingApiImpl();
    private static final UltimateServerApi SERVER = new UltimateServerApiImpl();
    private static final UltimateBankManagementApi BANKS = new UltimateBankManagementApiImpl();
    private static final UltimateShopManagementApi SHOPS = new UltimateShopManagementApiImpl();
    private static final UltimateHeistApi HEISTS = new UltimateHeistApiImpl();
    private static final UltimateEconomyApi ECONOMY = new UltimateEconomyApiImpl();

    private UltimateBankingApiProvider() {}

    public static UltimateBankingApi get() {
        return INSTANCE;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateServerApi server() {
        return SERVER;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateBankManagementApi banks() {
        return BANKS;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateShopManagementApi shops() {
        return SHOPS;
    }

    @ApiStatus.AvailableSince("2.0.0")
    public static UltimateHeistApi heists() {
        return HEISTS;
    }

    @ApiStatus.AvailableSince("2.1.0")
    public static UltimateEconomyApi economy() {
        return ECONOMY;
    }
}
