package com.dataart.softwarestore.service.hibernate;

import com.dataart.softwarestore.model.domain.Program;
import com.dataart.softwarestore.service.ProgramManager;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HibernateProgramManager implements ProgramManager {

    @Autowired
    private SessionFactory sessionFactory;

    private Session session() {
        return sessionFactory.getCurrentSession();
    }

    @Override
    @Transactional
    public void addProgram(Program program) {
        session().save(program);
    }

}