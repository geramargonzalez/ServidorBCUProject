package com.enlamano.server;

/**
 * Clase que representa la respuesta del web service SOAP del BCU
 */
public class BcuSoapResponse {
    
    private String moneda;
    private String fecha;
    private double compra;
    private double venta;
    private String fechaConsulta;
    
    public BcuSoapResponse() {
    }
    
    public BcuSoapResponse(String moneda, String fecha, double compra, double venta, String fechaConsulta) {
        this.moneda = moneda;
        this.fecha = fecha;
        this.compra = compra;
        this.venta = venta;
        this.fechaConsulta = fechaConsulta;
    }
    
    public String getMoneda() {
        return moneda;
    }
    
    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }
    
    public String getFecha() {
        return fecha;
    }
    
    public void setFecha(String fecha) {
        this.fecha = fecha;
    }
    
    public double getCompra() {
        return compra;
    }
    
    public void setCompra(double compra) {
        this.compra = compra;
    }
    
    public double getVenta() {
        return venta;
    }
    
    public void setVenta(double venta) {
        this.venta = venta;
    }
    
    public String getFechaConsulta() {
        return fechaConsulta;
    }
    
    public void setFechaConsulta(String fechaConsulta) {
        this.fechaConsulta = fechaConsulta;
    }
    
    @Override
    public String toString() {
        return "BcuSoapResponse{" +
                "moneda='" + moneda + '\'' +
                ", fecha='" + fecha + '\'' +
                ", compra=" + compra +
                ", venta=" + venta +
                ", fechaConsulta='" + fechaConsulta + '\'' +
                '}';
    }
}
