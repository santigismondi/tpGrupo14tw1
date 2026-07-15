package com.tallerwebi.presentacion.controller;

import com.tallerwebi.dominio.entity.Categoria;
import com.tallerwebi.dominio.entity.Cliente;
import com.tallerwebi.dominio.evento.InteraccionChatEvent;
import com.tallerwebi.dominio.interfaces.ServicioCliente;
import com.tallerwebi.presentacion.dto.CategoriaDto;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ControladorPortalCliente {

  private static final String ATTR_ERROR = "error";
  private static final String ATTR_CLIENTE = "cliente";
  private static final String REDIRECT_PORTAL_CLIENTES = "redirect:/portal/clientes";
  private static final String REDIRECT_COMPLETAR_DATOS =
    "redirect:/portal/clientes/completar-datos";
  private static final String REDIRECT_MIS_PEDIDOS = "redirect:/portal/clientes/mis-pedidos";

  private final ServicioCliente servicioCliente;

  private final ApplicationEventPublisher eventPublisher;

  @Autowired
  public ControladorPortalCliente(ServicioCliente servicioCliente, ApplicationEventPublisher eventPublisher) {
    this.servicioCliente = servicioCliente;
    this.eventPublisher = eventPublisher;
  }

  @ModelAttribute("categoria")
  public CategoriaDto categoriaTema() {
    return new CategoriaDto(new Categoria("servicio", true, "servicio"));
  }

  @GetMapping("/portal/clientes")
  public String mostrarLoginCliente(
    @RequestParam(value = "error", required = false) String error,
    Model model
  ) {
    if (error != null) {
      model.addAttribute(ATTR_ERROR, "Correo/DNI o contraseña incorrectos");
    }
    return "portalCliente/login";
  }

  @PostMapping("/portal/clientes/procesar")
  public String procesarLoginFallback() {
    return "redirect:/portal/clientes?error=true";
  }

  @GetMapping("/portal/clientes/registro")
  public String mostrarRegistroCliente(Model model) {
    model.addAttribute(ATTR_CLIENTE, new Cliente());
    return "portalCliente/registro";
  }

  @PostMapping("/portal/clientes/registro-procesar")
  public String procesarRegistroCliente(
    @ModelAttribute("cliente") Cliente cliente,
    Model model,
    HttpServletRequest request
  ) {
    try {
      String rawPassword = cliente.getPassword();
      servicioCliente.registrarCliente(cliente);

      // Registro exitoso, activación inmediata. Logueamos al usuario automáticamente:
      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        cliente,
        rawPassword,
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_CLIENTE"))
      );
      SecurityContextHolder.getContext().setAuthentication(auth);
      request
        .getSession()
        .setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

      return REDIRECT_MIS_PEDIDOS;
    } catch (Exception e) {
      model.addAttribute(ATTR_ERROR, e.getMessage());
      model.addAttribute(ATTR_CLIENTE, cliente);
      return "portalCliente/registro";
    }
  }

  @GetMapping("/portal/clientes/google-login")
  public String redirigirGoogleLoginCliente(HttpServletRequest request) {
    request.getSession().setAttribute("OAUTH_LOGIN_TYPE", "CLIENTE");
    return "redirect:/oauth2/authorization/google";
  }

  @GetMapping("/portal/clientes/completar-datos")
  public String mostrarCompletarDatosCliente(Authentication authentication, Model model) {
    Cliente cliente = obtenerClienteSesion(authentication);
    if (cliente == null) {
      return REDIRECT_PORTAL_CLIENTES;
    }
    if (!faltanDatosObligatorios(cliente)) {
      return REDIRECT_MIS_PEDIDOS;
    }
    model.addAttribute(ATTR_CLIENTE, cliente);
    return "portalCliente/completar-datos";
  }

  @PostMapping("/portal/clientes/completar-datos-procesar")
  public String procesarCompletarDatosCliente(
    @RequestParam(value = "documento", required = false) String documento,
    @RequestParam(value = "telefono", required = false) String telefono,
    Authentication authentication,
    Model model,
    HttpServletRequest request
  ) {
    Cliente cliente = obtenerClienteSesion(authentication);
    if (cliente == null) {
      return REDIRECT_PORTAL_CLIENTES;
    }
    try {
      if (
        documento == null ||
        documento.trim().isEmpty() ||
        telefono == null ||
        telefono.trim().isEmpty()
      ) {
        throw new Exception("Por favor, ingresa tanto tu número de DNI como tu teléfono celular.");
      }
      servicioCliente.actualizarDatosCliente(cliente, documento, telefono, cliente.getNombre());

      // Actualizar sesión de seguridad en Spring Security
      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        cliente,
        authentication.getCredentials(),
        authentication.getAuthorities()
      );
      SecurityContextHolder.getContext().setAuthentication(auth);
      request
        .getSession()
        .setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

      return REDIRECT_MIS_PEDIDOS;
    } catch (Exception e) {
      model.addAttribute(ATTR_ERROR, e.getMessage());
      model.addAttribute(ATTR_CLIENTE, cliente);
      return "portalCliente/completar-datos";
    }
  }

  @GetMapping("/portal/clientes/mis-pedidos")
  public String mostrarMisPedidos(Authentication authentication, Model model) {
    Cliente cliente = obtenerClienteSesion(authentication);
    if (cliente != null) {
      if (faltanDatosObligatorios(cliente)) {
        return REDIRECT_COMPLETAR_DATOS;
      }
      model.addAttribute("faltanDatos", false);
      model.addAttribute(ATTR_CLIENTE, cliente);
    }
    return "portalCliente/home";
  }

  @GetMapping("/portal/clientes/perfil")
  public String mostrarPerfilCliente(Authentication authentication, Model model) {
    Cliente cliente = obtenerClienteSesion(authentication);
    if (cliente == null) {
      return REDIRECT_PORTAL_CLIENTES;
    }
    model.addAttribute(ATTR_CLIENTE, cliente);
    return "portalCliente/perfil";
  }

  @PostMapping("/portal/clientes/perfil-guardar")
  public String guardarPerfilCliente(
    @RequestParam(value = "documento", required = false) String documento,
    @RequestParam(value = "telefono", required = false) String telefono,
    @RequestParam(value = "nombre", required = false) String nombre,
    Authentication authentication,
    Model model,
    HttpServletRequest request
  ) {
    Cliente cliente = obtenerClienteSesion(authentication);
    if (cliente == null) {
      return REDIRECT_PORTAL_CLIENTES;
    }
    try {
      servicioCliente.actualizarDatosCliente(cliente, documento, telefono, nombre);
      // Actualizar sesión de seguridad
      UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        cliente,
        authentication.getCredentials(),
        authentication.getAuthorities()
      );
      SecurityContextHolder.getContext().setAuthentication(auth);
      request
        .getSession()
        .setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

      model.addAttribute("exito", "¡Tus datos han sido actualizados correctamente!");
      model.addAttribute(ATTR_CLIENTE, cliente);
      return "portalCliente/perfil";
    } catch (Exception e) {
      model.addAttribute(ATTR_ERROR, e.getMessage());
      model.addAttribute(ATTR_CLIENTE, cliente);
      return "portalCliente/perfil";
    }
  }

  @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
  private Cliente obtenerClienteSesion(Authentication authentication) {
    if (authentication == null) return null;
    Object principal = authentication.getPrincipal();
    if (principal instanceof Cliente) {
      Cliente enSesion = (Cliente) principal;
      Cliente bd = null;
      if (enSesion.getEmail() != null && !enSesion.getEmail().trim().isEmpty()) {
        bd = servicioCliente.buscarPorEmail(enSesion.getEmail());
      }
      if (
        bd == null && enSesion.getDocumento() != null && !enSesion.getDocumento().trim().isEmpty()
      ) {
        bd = servicioCliente.buscarPorDocumento(enSesion.getDocumento());
      }
      return bd != null ? bd : enSesion;
    }
    return null;
  }

  private boolean faltanDatosObligatorios(Cliente cliente) {
    if (cliente == null) return false;
    return (
      cliente.getDocumento() == null ||
      cliente.getDocumento().trim().isEmpty() ||
      cliente.getTelefono() == null ||
      cliente.getTelefono().trim().isEmpty()
    );
  }

  @GetMapping("/portal/clientes/historial")
  public String mostrarHistorialPedidos(Authentication authentication, Model model) {
    Cliente cliente = obtenerClienteSesion(authentication);
    if (cliente == null) {
      return REDIRECT_PORTAL_CLIENTES;
    }
    if (faltanDatosObligatorios(cliente)) {
      return REDIRECT_COMPLETAR_DATOS;
    }
    model.addAttribute(ATTR_CLIENTE, cliente);
    return "portalCliente/historial";
  }

  @GetMapping("/portal/clientes/reportar")
  public String mostrarReportarPedido(Authentication authentication, Model model) {
    Cliente cliente = obtenerClienteSesion(authentication);
    if (cliente == null) {
      return REDIRECT_PORTAL_CLIENTES;
    }
    if (faltanDatosObligatorios(cliente)) {
      return REDIRECT_COMPLETAR_DATOS;
    }
    model.addAttribute(ATTR_CLIENTE, cliente);
    return "portalCliente/reportar";
  }

    @PostMapping("/reportar/accion")
    @ResponseBody // Para devolver JSON en lugar de una vista Thymeleaf
    public ResponseEntity<Map<String, Object>> procesarAccionChat(
            @RequestParam("reporteId") Long reporteId,
            @RequestParam("clienteId") Long clienteId,
            @RequestParam("accion") String accion) {

        // 1. PUBLICAR EL EVENTO (Patrón Observer)
        // Esto dispara sincrónicamente el ChatReporteObserver
        eventPublisher.publishEvent(new InteraccionChatEvent(reporteId, clienteId, accion));

        // 2. LEER EL RESULTADO
        // Como el observer es síncrono, para cuando esta línea se ejecuta,
        // el observer ya guardó la respuesta en la base de datos.

        // Simulación de búsqueda en BD del último mensaje generado por el sistema:
        // MensajeChat ultimoMensaje = servicioReporte.obtenerUltimoMensaje(reporteId);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("mensaje", "Respuesta obtenida de la BD generada por el Observer");
        respuesta.put("opciones", new String[]{"Actualizar reporte", "Dar por realizado", "Desestimar reporte"});

        return ResponseEntity.ok(respuesta);
    }
}
